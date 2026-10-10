package top.turboweb.struct.radix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * REST 路径基数树实现。
 * <p>树的边即单个路径段（段级 Trie，无压缩）：字面子节点按段原文索引，
 * 同一节点至多挂一个参数子节点（{@code :name} 占位段）。
 * put 时同槽异名参数视为冲突立即拒收，同名参数复用同一参数节点；
 * matchAny 按字面优先、参数兜底、失败回溯的深度优先顺序解析路径，
 * 命中返回携带参数绑定的 {@link MatchResult}。
 * get、exist、match 与本实现的单命中语义不符，一律抛出 UnsupportedOperationException。
 * <p>非并发安全：不加锁，写操作不做原子性保证。
 *
 * @param <O> 值类型
 */
public class RestRadixTree<O> implements RadixTree<RestRadixTree.MatchResult<O>> {

    /**
     * 匹配命中结果：命中模式上注册的值与路径参数绑定。
     *
     * @param <O> 值类型
     */
    static final class MatchResult<O> {

        /**
         * 命中模式上注册的值。
         */
        public final O value;

        /**
         * 路径参数绑定（参数名到路径段），不可变。
         */
        public final Map<String, String> params;

        MatchResult(O value, Map<String, String> params) {
            this.value = value;
            this.params = Map.copyOf(params);
        }
    }

    /**
     * 参数名规则：字母或下划线开头，后续为字母、数字或下划线。
     */
    private static final Pattern PARAM_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 不支持方法（get、exist、match）抛出的异常消息，主体与只读视图共用同一来源。
     */
    private static final String GET_NOT_SUPPORTED = "The rest radix tree does not support the get operation";
    private static final String EXIST_NOT_SUPPORTED = "The rest radix tree does not support the exist operation";
    private static final String MATCH_NOT_SUPPORTED = "The rest radix tree does not support the match operation";

    /**
     * 树节点：value 可空（根节点恒为 null），literals 按段原文索引，
     * paramChild 为参数子节点（至多一个，无则为 null），
     * paramName 仅在本节点是参数节点时为参数名（不带冒号），否则为 null。
     */
    private static final class Node<O> {

        private O value;
        private final Map<String, Node<O>> literals;
        private Node<O> paramChild;
        private String paramName;

        private Node() {
            this.literals = new HashMap<>();
        }
    }

    /**
     * 根节点：段为空、恒无值、不参与摘除。
     */
    private final Node<O> root;

    /**
     * 只读视图缓存：首次 {@link #toReadView()} 创建，此后复用同一实例。
     */
    private RadixTree<MatchResult<O>> readView;

    public RestRadixTree() {
        this.root = new Node<>();
    }

    /**
     * 不支持精确读取，恒抛 UnsupportedOperationException。
     */
    @Override
    public MatchResult<O> get(CharSequence key) {
        throw new UnsupportedOperationException(GET_NOT_SUPPORTED);
    }

    /**
     * 不支持存在性判断，恒抛 UnsupportedOperationException。
     */
    @Override
    public boolean exist(CharSequence key) {
        throw new UnsupportedOperationException(EXIST_NOT_SUPPORTED);
    }

    /**
     * 不支持多命中收集，恒抛 UnsupportedOperationException。
     */
    @Override
    public Set<MatchResult<O>> match(CharSequence key) {
        throw new UnsupportedOperationException(MATCH_NOT_SUPPORTED);
    }

    /**
     * 单命中匹配：按字面优先、参数兜底、失败回溯的深度优先顺序解析路径，
     * 返回携带参数绑定的命中结果；路径不以 {@code /} 开头或未命中返回 null，
     * path 为 null 时抛 IllegalArgumentException。
     */
    @Override
    public MatchResult<O> matchAny(CharSequence path) {
        if (path == null) {
            throw new IllegalArgumentException("The path argument was null");
        }
        String target = path.toString();
        if (!target.startsWith("/")) {
            return null;
        }
        List<String> segments = normalize(target);
        return walk(root, segments, 0, new HashMap<>());
    }

    /**
     * 注册模式并关联值，模式已存在时覆盖旧值。
     * <p>模式以 {@code /} 开头，按 {@code /} 分割为段，任何空段拒收；
     * 以 {@code :} 开头的整段为参数段，参数名须匹配 {@code [A-Za-z_][A-Za-z0-9_]*}；
     * 同一模式内参数名不得重复；同一槽位上与既有参数节点异名的参数视为冲突拒收。
     *
     * @param pattern 目标模式，不得为 null
     * @param value   关联值，不得为 null
     * @return 模式原先注册的值；模式原先不存在时返回 null
     * @throws IllegalArgumentException pattern 为 null、value 为 null、格式非法或参数冲突
     */
    public O put(String pattern, O value) {
        checkKeyNotNull(pattern);
        if (value == null) {
            throw new IllegalArgumentException("The value argument was null");
        }
        List<String> segments = parseSegments(pattern);
        Node<O> node = root;
        for (String segment : segments) {
            node = descendForPut(node, segment);
        }
        O old = node.value;
        node.value = value;
        return old;
    }

    /**
     * 按模式精确删除：与 put 同源校验，按模式串逐段下钻，
     * 任一段走不通或末节点无值返回 false；命中则摘除该模式注册的值，
     * 并向上折叠无值且无子的节点（根不摘）。
     *
     * @param pattern 目标模式，不得为 null
     * @return 是否发生了删除；模式未注册时返回 false
     * @throws IllegalArgumentException pattern 为 null 或格式非法
     */
    public boolean remove(String pattern) {
        checkKeyNotNull(pattern);
        List<String> segments = parseSegments(pattern);
        List<Node<O>> path = new ArrayList<>();
        path.add(root);
        Node<O> node = root;
        for (String segment : segments) {
            node = descendForRemove(node, segment);
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
     * 返回只读视图。视图为活视图而非快照：内部持有本实例，
     * 此后的 put / remove 对视图可见；重复调用返回同一实例。
     *
     * @return 只读视图
     */
    public RadixTree<MatchResult<O>> toReadView() {
        if (readView == null) {
            readView = new ReadView();
        }
        return readView;
    }

    /**
     * 回溯匹配：到达段数时节点有值即命中（params 经 Map.copyOf 封装为不可变）；
     * 否则先试字面子节点，失败再试参数分支（绑定后下钻，失败撤销绑定），
     * 两分支皆败返回 null，由上层回溯改试其备选分支。
     * params 为单次调用的局部 Map，命中封装副本、未命中整体丢弃，匹配对树无副作用。
     */
    private MatchResult<O> walk(Node<O> node, List<String> segments, int index, Map<String, String> params) {
        if (index == segments.size()) {
            return node.value == null ? null : new MatchResult<>(node.value, params);
        }
        String segment = segments.get(index);
        Node<O> literal = node.literals.get(segment);
        if (literal != null) {
            MatchResult<O> hit = walk(literal, segments, index + 1, params);
            if (hit != null) {
                return hit;
            }
        }
        Node<O> param = node.paramChild;
        if (param != null) {
            params.put(param.paramName, segment);
            MatchResult<O> hit = walk(param, segments, index + 1, params);
            if (hit != null) {
                return hit;
            }
            params.remove(param.paramName);
        }
        return null;
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
     * put 下钻一步：参数段在无参数子节点时新建（名字去掉冒号），
     * 已有且异名抛冲突，同名复用；字面段取既有子节点，无则新建挂上。
     */
    private Node<O> descendForPut(Node<O> node, String segment) {
        if (isParamSegment(segment)) {
            String name = segment.substring(1);
            if (node.paramChild == null) {
                Node<O> param = new Node<>();
                param.paramName = name;
                node.paramChild = param;
                return param;
            }
            if (!node.paramChild.paramName.equals(name)) {
                throw new IllegalArgumentException("Conflicting path variable ':" + name
                        + "' with existing ':" + node.paramChild.paramName + "'");
            }
            return node.paramChild;
        }
        return node.literals.computeIfAbsent(segment, key -> new Node<>());
    }

    /**
     * 摘值后自末节点向上折叠：无值且无任何子节点（literals 空且 paramChild 为 null）的节点
     * 从父摘除——字面子节点走 literals.remove(段)，参数子节点走 paramChild 置 null——
     * 并继续上溯，遇有值或有子的节点即停；根不摘。
     */
    private static <O> void foldUp(List<Node<O>> path, List<String> segments) {
        for (int depth = path.size() - 1; depth >= 1; depth--) {
            Node<O> node = path.get(depth);
            if (node.value != null || !node.literals.isEmpty() || node.paramChild != null) {
                break;
            }
            Node<O> parent = path.get(depth - 1);
            if (parent.paramChild == node) {
                parent.paramChild = null;
            } else {
                parent.literals.remove(segments.get(depth - 1));
            }
        }
    }

    /**
     * remove 下钻一步：参数段要求参数子节点存在且名字与模式一致，
     * 字面段要求字面子节点存在；走不通返回 null。
     */
    private Node<O> descendForRemove(Node<O> node, String segment) {
        if (isParamSegment(segment)) {
            Node<O> param = node.paramChild;
            if (param == null || !param.paramName.equals(segment.substring(1))) {
                return null;
            }
            return param;
        }
        return node.literals.get(segment);
    }

    /**
     * 模式解析与格式校验：{@code /} 开头，按 {@code /} 分割且保留尾部空段，
     * 逐段校验空段、参数段语法、同模式参数名重复。
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
            }
            segments.add(segment);
        }
        return segments;
    }

    private static boolean isParamSegment(String segment) {
        return segment.charAt(0) == ':';
    }

    private static void checkKeyNotNull(String pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException("The key argument was null");
        }
    }

    /**
     * 只读视图：matchAny 委托给外部实例，get、exist、match 与外部实例同样
     * 抛出 UnsupportedOperationException；写侧后续 put / remove 对本视图可见
     * （活视图，非快照）。仅能经 {@link RestRadixTree#toReadView()} 获得。
     */
    private final class ReadView implements RadixTree<MatchResult<O>> {

        @Override
        public MatchResult<O> get(CharSequence key) {
            throw new UnsupportedOperationException(GET_NOT_SUPPORTED);
        }

        @Override
        public boolean exist(CharSequence key) {
            throw new UnsupportedOperationException(EXIST_NOT_SUPPORTED);
        }

        @Override
        public Set<MatchResult<O>> match(CharSequence key) {
            throw new UnsupportedOperationException(MATCH_NOT_SUPPORTED);
        }

        @Override
        public MatchResult<O> matchAny(CharSequence path) {
            return RestRadixTree.this.matchAny(path);
        }
    }
}
