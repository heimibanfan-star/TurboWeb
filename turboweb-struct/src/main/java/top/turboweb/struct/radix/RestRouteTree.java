package top.turboweb.struct.radix;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * REST 路径基数树实现。
 * <p>树的边即单个路径段（段级 Trie，无压缩）：字面子节点按段原文索引，
 * 参数子节点（{@code :name} 占位段）按参数名索引，同一节点可并存多个互为兄弟分支。
 * put 时按匹配同款口径做冲突检测：逐段比对，字面量相同或双方均为参数则继续向后，
 * 字面量相异或一方为字面量一方为参数即分叉（字面量优先）不歧义，逐段对齐走到尽头
 * 即冲突拒收——既有模式与新模式相同属重复注册，不同属歧义路径；不歧义的同槽异名
 * 参数作为兄弟参数分支共存，同名参数复用同一参数节点；
 * matchAny 穷举全部可行分支收集全部命中，按类型签名升序取最小（签名逐位记段类型，
 * 字面 0 参数 1，最小即在最先分型的段位上字面量优先，与 put 冲突检测的字面量优先口径一致），
 * 命中返回携带参数绑定的 {@link RouteTree.Result}；match 单命中委托 matchAny。构造器可开启查询优化：
 * 纯字面量模式进入哈希快表，匹配先查快表未果再走树，对外语义不变。
 * get 与 exist 按注册模式原文精确匹配：归一化后逐段以段原文比对，不解析参数
 * ——冒号段视为普通字符串、须与同名参数边对齐，普通段只走字面边，不做通配绑定，
 * 命中返回空参数绑定；
 * {@link #toReadView()} 返回只读视图（仅暴露 match）并将本树封存为只读，封存后 put 与 remove
 * 一律抛出 IllegalStateException。
 * <p>非并发安全：不加锁，写操作不做原子性保证。
 *
 * @param <O> 值类型
 */
public class RestRouteTree<O> implements RouteTree<O> {

    /**
     * 参数名规则：字母或下划线开头，后续为字母、数字或下划线。
     */
    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 只读封存后写操作抛出的异常消息。
     */
    private static final String WRITE_NOT_ALLOWED_AFTER_TO_READ_VIEW =
            "The rest radix tree does not allow write operations after toReadView()";

    /**
     * 树节点：value 可空（根节点恒为 null），literals 按段原文索引字面子节点，
     * params 按参数名（不带冒号）索引参数子节点，可并存多个互为兄弟分支。
     */
    private static final class Node<O> {

        private O value;
        private final Map<String, Node<O>> literals;
        private final Map<String, Node<O>> params;

        private Node() {
            this.literals = new HashMap<>();
            this.params = new HashMap<>();
        }
    }

    /**
     * 根节点：段为空、恒无值、不参与摘除。
     */
    private final Node<O> root;

    /**
     * 字面路径快表：查询优化开启时注册纯字面量模式的哈希表，键为 {@code /} 连接的
     * 归一化段序列（与 matchAny 的归一化同口径）；未开启时为 null——null 即未开启，
     * 作为全部快路径访问点的统一判据。
     */
    private final Map<String, O> literalPaths;

    /**
     * 只读视图缓存：首次 {@link #toReadView()} 创建，此后复用同一实例。
     */
    private RouteTree<O> readView;

    /**
     * 只读封存标记：{@link #toReadView()} 调用起置真，此后所有写操作拒绝。
     */
    private final AtomicBoolean readViewSealed = new AtomicBoolean(false);

    /**
     * 构造未开启查询优化的空树，全部模式按原逻辑进树。
     */
    public RestRouteTree() {
        this(false);
    }

    /**
     * @param literalFastPath 查询优化开关：开启时纯字面量模式进入哈希快表，
     *                        匹配先查快表未果再走树；未开启时全部模式按原逻辑进树。
     *                        开关只改变存储与查找的路径，不改变注册、冲突、匹配、
     *                        删除的任何对外语义（纯字面量模式与含参模式在参数位必然
     *                        分叉不歧义，快表内重复注册即其唯一冲突形态）
     */
    public RestRouteTree(boolean literalFastPath) {
        this.root = new Node<>();
        this.literalPaths = literalFastPath ? new HashMap<>() : null;
    }

    /**
     * 精确模式取值：不解析参数，键归一化后逐段与注册模式原文比对——以 {@code :}
     * 开头的段视为普通字符串、须命中同名参数边，其余段只走字面边，不做通配绑定，
     * 命中返回空参数绑定的命中结果；键不以 {@code /} 开头、任一段走不通或末节点
     * 无值时返回 null；查询优化开启时先查字面快表，未果再走树。
     *
     * @param key 目标键，不得为 null
     * @return 命中结果；键未注册时返回 null
     * @throws IllegalArgumentException key 为 null
     */
    public Result<O> get(CharSequence key) {
        O value = exactPatternValue(key);
        return value == null ? null : new Result<>(value, new HashMap<>());
    }

    /**
     * 精确存在性判断：与 {@link #get(CharSequence)} 同一命中口径，键与注册模式
     * 原文精确命中时返回 true。
     *
     * @param key 目标键，不得为 null
     * @return 键已注册返回 true，否则返回 false
     * @throws IllegalArgumentException key 为 null
     */
    public boolean exist(CharSequence key) {
        return exactPatternValue(key) != null;
    }

    /**
     * 单命中匹配：委托 {@link #matchAny(CharSequence)}，命中与择优口径与其完全一致。
     *
     * @param key 目标键，不得为 null
     * @return 命中结果；无命中时返回 null
     */
    @Override
    public Result<O> match(CharSequence key) {
        return matchAny(key);
    }

    /**
     * 单命中匹配：查询优化开启时先查字面快表（键为归一化段序列，命中即注册时的
     * 纯字面量模式——全字面签名必然最小，快表命中即整体最优）；未命中再穷举
     * 全部可行分支收集全部命中，按类型签名升序取最小者，返回携带参数绑定的
     * 命中结果；路径不以 {@code /} 开头或未命中返回 null，
     * path 为 null 时抛 IllegalArgumentException。
     */
    public Result<O> matchAny(CharSequence path) {
        if (path == null) {
            throw new IllegalArgumentException("The path argument was null");
        }
        String target = path.toString();
        if (!target.startsWith("/")) {
            return null;
        }
        List<String> segments = normalize(target);
        if (literalPaths != null) {
            O literal = literalPaths.get(String.join("/", segments));
            if (literal != null) {
                return new Result<>(literal, new HashMap<>());
            }
        }
        List<Hit<O>> hits = new ArrayList<>();
        walk(root, segments, hits);
        if (hits.isEmpty()) {
            return null;
        }
        hits.sort((a, b) -> Arrays.compare(a.signature, b.signature));
        return hits.getFirst().result;
    }

    /**
     * 注册模式并关联值；模式已注册（重复注册）或与既有注册面歧义时拒收，
     * 抛出异常且树保持不变。
     * <p>模式以 {@code /} 开头，按 {@code /} 分割为段，任何空段拒收；
     * 以 {@code :} 开头的整段为参数段，参数名须匹配 {@code [A-Za-z_][A-Za-z0-9_]*}；
     * 同一模式内参数名不得重复；字面段内不得出现 {@code :}（冒号仅允许作为参数段前缀）。
     * <p>查询优化开启时，纯字面量模式（不含任何参数段）进入哈希快表而非树：
     * 纯字面量模式与含参模式在参数位必然分叉（字面量优先）不歧义，快表内键重复
     * 即重复注册，为唯一冲突形态；未开启时全部模式走下述树逻辑。
     * <p>冲突检测与匹配同口径逐段比对新模式与既有注册面：字面量相同或双方均为参数
     * 继续向后，字面量相异或字面量对参数即分叉（字面量优先）不歧义；与既有模式在
     * 参数点对齐时逐支回溯比对。逐段对齐走到尽头且既有节点有值即冲突——既有模式与
     * 新模式相同为重复注册、不同为歧义路径，均拒收；不歧义的同槽异名参数作为
     * 兄弟参数分支共存。
     *
     * @param pattern 目标模式，不得为 null
     * @param value   关联值，不得为 null
     * @throws IllegalArgumentException pattern 为 null、value 为 null、格式非法、
     *         重复注册已存在的模式或与既有模式歧义
     * @throws IllegalStateException 已调用 {@link #toReadView()} 封存为只读后
     */
    public void put(String pattern, O value) {
        ensureNotSealed();
        checkKeyNotNull(pattern);
        if (value == null) {
            throw new IllegalArgumentException("The value argument was null");
        }
        List<String> segments = parseSegments(pattern);
        if (literalPaths != null && segments.stream().noneMatch(RestRouteTree::isParamSegment)) {
            String key = String.join("/", segments);
            if (literalPaths.containsKey(key)) {
                throw new IllegalArgumentException("Path pattern '" + pattern + "' is already registered");
            }
            literalPaths.put(key, value);
            return;
        }
        String conflict = findConflict(root, segments);
        if (conflict != null) {
            throw new IllegalArgumentException(conflict.equals(pattern)
                    ? "Path pattern '" + pattern + "' is already registered"
                    : "Ambiguous path pattern '" + pattern + "' conflicts with existing pattern '" + conflict + "'");
        }
        Node<O> node = root;
        for (String segment : segments) {
            node = descendForPut(node, segment);
        }
        node.value = value;
    }

    /**
     * 冲突检测：按匹配同款口径逐段比对新模式与既有注册面。
     * 新模式字面段只与同名既有字面子节点对齐下探，新模式参数段与全部既有参数子节点
     * 逐支对齐下探（一支走穷即回溯改试下一支）；字面量与参数同槽不比对，字面量优先
     * 不歧义；逐段对齐走到新模式尽头且节点有值，即与既有注册冲突，返回自根重建的
     * 该既有模式串（与新模式相同即重复注册，不同即歧义路径）；
     * 全部分支走穷无对齐命中返回 null；既有模式串由自根累积的段前缀重建，随回溯撤销。
     */
    private String findConflict(Node<O> start, List<String> segments) {
        int size = segments.size();
        StringBuilder path = new StringBuilder();
        Deque<ConflictFrame<O>> stack = new ArrayDeque<>();
        stack.push(new ConflictFrame<>(start, 0, path.length()));
        boolean childFailed = false;
        while (!stack.isEmpty()) {
            ConflictFrame<O> frame = stack.peek();
            if (childFailed) {
                childFailed = false;
                if (isParamSegment(segments.get(frame.index))) {
                    path.setLength(frame.mark);
                } else {
                    stack.pop();
                    childFailed = true;
                    continue;
                }
            }
            if (frame.index == size) {
                if (frame.node.value != null) {
                    return path.toString();
                }
                stack.pop();
                childFailed = true;
                continue;
            }
            String segment = segments.get(frame.index);
            if (isParamSegment(segment)) {
                if (frame.paramIt == null) {
                    frame.paramIt = frame.node.params.entrySet().iterator();
                }
                if (!frame.paramIt.hasNext()) {
                    stack.pop();
                    childFailed = true;
                    continue;
                }
                Map.Entry<String, Node<O>> entry = frame.paramIt.next();
                path.append("/:").append(entry.getKey());
                stack.push(new ConflictFrame<>(entry.getValue(), frame.index + 1, path.length()));
                continue;
            }
            Node<O> literal = frame.node.literals.get(segment);
            if (literal == null) {
                stack.pop();
                childFailed = true;
                continue;
            }
            path.append('/').append(segment);
            stack.push(new ConflictFrame<>(literal, frame.index + 1, path.length()));
        }
        return null;
    }

    /**
     * findConflict 显式栈帧：记本层节点与段下标、参数边迭代游标，mark 为本层进入时
     * 模式前缀的长度基线（子支走穷后据此回退累积串）。
     */
    private static final class ConflictFrame<O> {

        private final Node<O> node;
        private final int index;
        private final int mark;
        private Iterator<Map.Entry<String, Node<O>>> paramIt;

        private ConflictFrame(Node<O> node, int index, int mark) {
            this.node = node;
            this.index = index;
            this.mark = mark;
        }
    }

    /**
     * 按模式精确删除：与 put 同源校验；查询优化开启时纯字面量模式自快表删除，
     * 其余按模式串逐段下钻，任一段走不通或末节点无值返回 false；命中则摘除
     * 该模式注册的值，并向上折叠无值且无子的节点（根不摘）。
     *
     * @param pattern 目标模式，不得为 null
     * @return 是否发生了删除；模式未注册时返回 false
     * @throws IllegalArgumentException pattern 为 null 或格式非法
     * @throws IllegalStateException 已调用 {@link #toReadView()} 封存为只读后
     */
    public boolean remove(String pattern) {
        ensureNotSealed();
        checkKeyNotNull(pattern);
        List<String> segments = parseSegments(pattern);
        if (literalPaths != null && segments.stream().noneMatch(RestRouteTree::isParamSegment)) {
            return literalPaths.remove(String.join("/", segments)) != null;
        }
        List<Node<O>> path = new ArrayList<>();
        path.add(root);
        Node<O> node = root;
        for (String segment : segments) {
            node = descendExact(node, segment);
            if (node == null) {
                return false;
            }
            path.add(node);
        }
        if (node.value == null) {
            return false;
        }
        node.value = null;
        foldUp(path, segments);
        return true;
    }

    /**
     * 返回只读视图，同时将本树封存为只读：自本次调用起所有写操作（put、remove）
     * 一律抛出 {@link IllegalStateException}，封存前注册的内容对视图可见。
     * 视图为活视图而非快照：内部持有本实例；重复调用返回同一实例且封存状态不变。
     *
     * @return 只读视图
     */
    public RouteTree<O> toReadView() {
        readViewSealed.set(true);
        if (readView == null) {
            readView = new ReadView();
        }
        return readView;
    }

    /**
     * 回溯匹配：深度优先穷举全部可行分支，逐个命中收集进 hits，不做首命中即停
     * ——参数兄弟分支的子树可能在更深层才分型，首命中不保证全局字面量优先，
     * 最终由 matchAny 按签名升序取最小。签名逐位记段类型（字面 false、参数
     * true，按位比较字面小于参数）。到达段数时节点有值即为一个命中（params
     * 经 Map.copyOf 封装为不可变，签名克隆当前轨迹留存）；params 为单次调用的
     * 局部 Map，命中封装副本、未命中撤销绑定整体丢弃，匹配对树无副作用。
     * types 为当前路径的段类型轨迹。
     */
    private void walk(Node<O> start, List<String> segments, List<Hit<O>> hits) {
        int size = segments.size();
        Map<String, String> params = new HashMap<>();
        boolean[] types = new boolean[size];
        Deque<WalkFrame<O>> stack = new ArrayDeque<>();
        stack.push(new WalkFrame<>(start, 0));
        boolean childReturned = false;
        while (!stack.isEmpty()) {
            WalkFrame<O> frame = stack.peek();
            if (childReturned) {
                childReturned = false;
                if (frame.undoKey != null) {
                    params.remove(frame.undoKey);
                }
            }
            if (frame.index == size) {
                if (frame.node.value != null) {
                    hits.add(new Hit<>(new Result<>(frame.node.value, params), types.clone()));
                }
                stack.pop();
                childReturned = true;
                continue;
            }
            String segment = segments.get(frame.index);
            if (!frame.literalDone) {
                frame.literalDone = true;
                Node<O> literal = frame.node.literals.get(segment);
                if (literal != null) {
                    types[frame.index] = false;
                    stack.push(new WalkFrame<>(literal, frame.index + 1));
                    continue;
                }
            }
            if (frame.paramIt == null) {
                frame.paramIt = frame.node.params.entrySet().iterator();
            }
            if (frame.paramIt.hasNext()) {
                Map.Entry<String, Node<O>> entry = frame.paramIt.next();
                types[frame.index] = true;
                params.put(entry.getKey(), segment);
                frame.undoKey = entry.getKey();
                stack.push(new WalkFrame<>(entry.getValue(), frame.index + 1));
                continue;
            }
            stack.pop();
            childReturned = true;
        }
    }

    /**
     * walk 显式栈帧：记本层子分支遍历进度——字面边是否已试、参数边迭代游标、
     * 最近一次下钻所绑定参数边的键（子层返回后据此撤销绑定）。
     */
    private static final class WalkFrame<O> {

        private final Node<O> node;
        private final int index;
        private boolean literalDone;
        private Iterator<Map.Entry<String, Node<O>>> paramIt;
        private String undoKey;

        private WalkFrame(Node<O> node, int index) {
            this.node = node;
            this.index = index;
        }
    }

    /**
     * 命中候选：命中结果与克隆留存的类型签名，供按签名升序择优。
     */
    private record Hit<O>(Result<O> result, boolean[] signature) {
    }

    /**
     * 路径归一化：按 {@code /} 分割并过滤空段（首尾斜杠与连续斜杠同此吞掉），
     * {@code /a//b}、{@code /a/b/}、{@code ///a/b///} 均视同 {@code /a/b}；剥后为空即零段。
     */
    private static List<String> normalize(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * 按注册模式原文精确取值，get 与 exist 共用：判空先行；键不以 {@code /} 开头
     * 按未命中处理；归一化后快表开启时先查快表，未果再走树。树下钻与 remove 同源：
     * 冒号段按名称走参数边、普通段走字面边，逐段全对齐且末节点有值即命中——故
     * get 能按原文取回含参模式（如 {@code /a/:b}），但取不回其通配路径（如
     * {@code /a/x}），也不做参数绑定。
     */
    private O exactPatternValue(CharSequence key) {
        checkKeyNotNull(key);
        String target = key.toString();
        if (!target.startsWith("/")) {
            return null;
        }
        List<String> segments = normalize(target);
        if (literalPaths != null) {
            O literal = literalPaths.get(String.join("/", segments));
            if (literal != null) {
                return literal;
            }
        }
        Node<O> node = root;
        for (String segment : segments) {
            node = descendExact(node, segment);
            if (node == null) {
                return null;
            }
        }
        return node.value;
    }

    /**
     * put 下钻一步：参数段按参数名取既有参数子节点，无则新建（冲突已在 put 前置检测拦下）；
     * 字面段取既有子节点，无则新建挂上。
     */
    private Node<O> descendForPut(Node<O> node, String segment) {
        if (isParamSegment(segment)) {
            return node.params.computeIfAbsent(segment.substring(1), key -> new Node<>());
        }
        return node.literals.computeIfAbsent(segment, key -> new Node<>());
    }

    /**
     * 摘值后自末节点向上折叠：无值且无任何子节点（literals 与 params 皆空）的节点
     * 从父摘除——模式段为参数段时按参数名摘 params 条目，字面段按键摘 literals 条目——
     * 并继续上溯，遇有值或有子的节点即停；根不摘。
     */
    private static <O> void foldUp(List<Node<O>> path, List<String> segments) {
        for (int depth = path.size() - 1; depth >= 1; depth--) {
            Node<O> node = path.get(depth);
            if (node.value != null || !node.literals.isEmpty() || !node.params.isEmpty()) {
                break;
            }
            String segment = segments.get(depth - 1);
            Node<O> parent = path.get(depth - 1);
            if (isParamSegment(segment)) {
                parent.params.remove(segment.substring(1));
            } else {
                parent.literals.remove(segment);
            }
        }
    }

    /**
     * remove、get、exist 共用的精确下钻一步：参数段按参数名取参数子节点
     * （键即模式名，取到即同名），字面段按键取字面子节点；走不通返回 null。
     */
    private Node<O> descendExact(Node<O> node, String segment) {
        if (isParamSegment(segment)) {
            return node.params.get(segment.substring(1));
        }
        return node.literals.get(segment);
    }

    /**
     * 模式解析与格式校验：{@code /} 开头，按 {@code /} 分割且保留尾部空段，
     * 逐段校验空段、参数段语法、字面段冒号、同模式参数名重复。
     */
    private static List<String> parseSegments(String pattern) {
        if (!pattern.startsWith("/")) {
            throw new IllegalArgumentException("The key must start with '/'");
        }
        String[] raw = pattern.substring(1).split("/", -1);
        List<String> segments = new ArrayList<>(raw.length);
        Set<String> names = new HashSet<>();
        for (String segment : raw) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Empty path segment is not allowed");
            }
            if (isParamSegment(segment)) {
                String name = segment.substring(1);
                if (!PARAM_NAME_PATTERN.matcher(name).matches()) {
                    throw new IllegalArgumentException("Invalid path variable: " + segment);
                }
                if (!names.add(name)) {
                    throw new IllegalArgumentException("Duplicate path variable '" + name + "' in path");
                }
            } else if (segment.contains(":")) {
                throw new IllegalArgumentException("Literal path segment must not contain ':': " + segment);
            }
            segments.add(segment);
        }
        return segments;
    }

    private static boolean isParamSegment(String segment) {
        return segment.charAt(0) == ':';
    }

    private static void checkKeyNotNull(CharSequence key) {
        if (key == null) {
            throw new IllegalArgumentException("The key argument was null");
        }
    }

    /**
     * 写操作前置校验：已封存为只读（调用过 {@link #toReadView()}）即拒绝，
     * 先于参数校验生效。
     */
    private void ensureNotSealed() {
        if (readViewSealed.get()) {
            throw new IllegalStateException(WRITE_NOT_ALLOWED_AFTER_TO_READ_VIEW);
        }
    }

    /**
     * 只读视图：match 委托给外部实例；封存（{@link RestRouteTree#toReadView()}）
     * 前注册的内容对本视图可见（活视图，非快照），封存后外部实例拒绝一切写操作。
     * 仅能经 {@link RestRouteTree#toReadView()} 获得。
     */
    private final class ReadView implements RouteTree<O> {

        @Override
        public Result<O> match(CharSequence key) {
            return RestRouteTree.this.match(key);
        }
    }
}
