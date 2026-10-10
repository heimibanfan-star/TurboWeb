package top.turboweb.struct.radix;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 可读可写的基数树实现。
 * <p>非并发安全：不加锁，写操作不做原子性保证。
 * 边以传入键的子序列视图存储；子节点按入边首字符索引。
 * 读方法面齐全：match 收集全部前缀命中，matchAny 取最深的命中。
 *
 * @param <O> 值类型
 */
public class StandardRadixTree<O> implements RadixTree<O> {

    /**
     * 树节点：edge 为入边字符序列，value 可空（根节点恒为 null），
     * children 按入边首字符索引，键为 edge 的首字符。
     */
    private static final class Node<O> {

        private CharSequence edge;
        private O value;
        private Map<Character, Node<O>> children;

        private Node(CharSequence edge, O value) {
            this(edge, value, new HashMap<>());
        }

        private Node(CharSequence edge, O value, Map<Character, Node<O>> children) {
            this.edge = edge;
            this.value = value;
            this.children = children;
        }
    }

    /**
     * 根节点：edge 为空串，不可删除、永不参与合并。
     */
    private final Node<O> root;

    /**
     * 只读视图缓存：首次 {@link #toReadView()} 创建，此后复用同一实例。
     */
    private RadixTree<O> readView;

    public StandardRadixTree() {
        this.root = new Node<>("", null);
    }

    @Override
    public O get(CharSequence key) {
        checkKeyNotNull(key);
        Node<O> node = searchExact(key);
        return node == null ? null : node.value;
    }

    @Override
    public boolean exist(CharSequence key) {
        return get(key) != null;
    }

    @Override
    public Set<O> match(CharSequence key) {
        checkKeyNotNull(key);
        Set<O> hits = new LinkedHashSet<>();
        Node<O> current = root;
        int matched = 0;
        while (matched < key.length()) {
            Node<O> child = current.children.get(key.charAt(matched));
            if (child == null) {
                break;
            }
            CharSequence edge = child.edge;
            int edgeLen = edge.length();
            // key 止于边中时该节点不算命中：其完整路径比 key 长，不可能是 key 的前缀键
            if (matched + edgeLen > key.length()) {
                break;
            }
            boolean edgeMatched = true;
            for (int i = 0; i < edgeLen; i++) {
                if (edge.charAt(i) != key.charAt(matched + i)) {
                    edgeMatched = false;
                    break;
                }
            }
            if (!edgeMatched) {
                break;
            }
            matched += edgeLen;
            current = child;
            if (current.value != null) {
                hits.add(current.value);
            }
        }
        return hits;
    }

    /**
     * 任一命中读取：取最深的命中（最长前缀）的值；
     * 无命中返回 null，key 为 null 时抛 IllegalArgumentException。
     */
    @Override
    public O matchAny(CharSequence key) {
        O any = null;
        for (O hit : match(key)) {
            any = hit;
        }
        return any;
    }

    /**
     * 注册键并关联值，键已存在时覆盖旧值。
     *
     * @param key   目标键，不得为 null 且长度不得为 0
     * @param value 关联值，不得为 null
     * @return 键原先注册的值；键原先不存在时返回 null
     * @throws IllegalArgumentException key 为 null 或零长度，或 value 为 null
     */
    public O put(CharSequence key, O value) {
        checkKeyNotNull(key);
        if (key.length() == 0) {
            throw new IllegalArgumentException("The key argument was zero-length");
        }
        if (value == null) {
            throw new IllegalArgumentException("The value argument was null");
        }
        Node<O> node = root;
        int matched = 0;
        while (true) {
            if (matched == key.length()) {
                O old = node.value;
                node.value = value;
                return old;
            }
            char first = key.charAt(matched);
            Node<O> child = node.children.get(first);
            if (child == null) {
                node.children.put(first, new Node<>(key.subSequence(matched, key.length()), value));
                return null;
            }
            CharSequence edge = child.edge;
            int common = commonPrefixLength(edge, key, matched);
            if (common == edge.length()) {
                matched += edge.length();
                node = child;
                continue;
            }
            if (common == key.length() - matched) {
                // key 止于边中：拆两段，新建子节点承接原边剩余部分与原值、原 children，
                // 当前节点 edge 截为公共前缀、值置为新值
                Node<O> tail = new Node<>(edge.subSequence(common, edge.length()), child.value, child.children);
                child.edge = edge.subSequence(0, common);
                child.value = value;
                child.children = new HashMap<>();
                child.children.put(tail.edge.charAt(0), tail);
                return null;
            }
            // 边中分叉：拆三段，先建两子节点（key 剩余 + 新值；原边剩余 + 原值 + 原 children），
            // 再原地改当前节点（edge 置公共前缀、value 置 null、children 置两子）
            Node<O> keyTail = new Node<>(key.subSequence(matched + common, key.length()), value);
            Node<O> edgeTail = new Node<>(edge.subSequence(common, edge.length()), child.value, child.children);
            child.edge = edge.subSequence(0, common);
            child.value = null;
            child.children = new HashMap<>();
            child.children.put(keyTail.edge.charAt(0), keyTail);
            child.children.put(edgeTail.edge.charAt(0), edgeTail);
            return null;
        }
    }

    /**
     * 精确匹配删除。
     *
     * @param key 目标键，不得为 null
     * @return 是否发生了删除；键不存在或未注册值时返回 false
     * @throws IllegalArgumentException key 为 null
     */
    public boolean remove(CharSequence key) {
        checkKeyNotNull(key);
        Node<O> node = root;
        Node<O> parent = null;
        int matched = 0;
        while (true) {
            if (matched == key.length()) {
                if (node.value == null) {
                    return false;
                }
                removeValue(node, parent);
                return true;
            }
            Node<O> child = node.children.get(key.charAt(matched));
            if (child == null) {
                return false;
            }
            CharSequence edge = child.edge;
            int edgeLen = edge.length();
            if (matched + edgeLen > key.length()) {
                return false;
            }
            for (int i = 0; i < edgeLen; i++) {
                if (edge.charAt(i) != key.charAt(matched + i)) {
                    return false;
                }
            }
            matched += edgeLen;
            parent = node;
            node = child;
        }
    }

    /**
     * 返回只读视图。视图为活视图而非快照：内部持有本实例，
     * 此后的 put / remove 对视图可见；重复调用返回同一实例。
     *
     * @return 只读视图
     */
    public RadixTree<O> toReadView() {
        if (readView == null) {
            readView = new ReadView();
        }
        return readView;
    }

    /**
     * 精确搜索：返回与 key 完全对应的节点；无对应节点返回 null。
     * 零长度 key 停在根节点（根无值，由调用方判定）。
     */
    private Node<O> searchExact(CharSequence key) {
        Node<O> node = root;
        int matched = 0;
        while (matched < key.length()) {
            Node<O> child = node.children.get(key.charAt(matched));
            if (child == null) {
                return null;
            }
            CharSequence edge = child.edge;
            int edgeLen = edge.length();
            if (matched + edgeLen > key.length()) {
                return null;
            }
            for (int i = 0; i < edgeLen; i++) {
                if (edge.charAt(i) != key.charAt(matched + i)) {
                    return null;
                }
            }
            matched += edgeLen;
            node = child;
        }
        return node;
    }

    /**
     * 摘除节点值并按子节点数做结构还原：
     * 无 children 时从父摘除本节点并做父合并检查；恰一个子节点时与子合并；
     * 两个及以上子节点时仅清值。根节点恒不参与合并。
     */
    private void removeValue(Node<O> node, Node<O> parent) {
        if (node.children.isEmpty()) {
            parent.children.remove(node.edge.charAt(0));
            // 摘除后父无值且恰剩一个子节点则原地合并；父在祖父中的引用不变，无需上溯
            if (parent != root && parent.value == null && parent.children.size() == 1) {
                Node<O> remaining = onlyChild(parent);
                parent.edge = concatenate(parent.edge, remaining.edge);
                parent.value = remaining.value;
                parent.children = remaining.children;
            }
        } else if (node.children.size() == 1) {
            Node<O> child = onlyChild(node);
            node.edge = concatenate(node.edge, child.edge);
            node.value = child.value;
            node.children = child.children;
        } else {
            node.value = null;
        }
    }

    private static void checkKeyNotNull(CharSequence key) {
        if (key == null) {
            throw new IllegalArgumentException("The key argument was null");
        }
    }

    private static int commonPrefixLength(CharSequence edge, CharSequence key, int from) {
        int limit = Math.min(edge.length(), key.length() - from);
        int length = 0;
        while (length < limit && edge.charAt(length) == key.charAt(from + length)) {
            length++;
        }
        return length;
    }

    private static <O> Node<O> onlyChild(Node<O> node) {
        return node.children.values().iterator().next();
    }

    private static CharSequence concatenate(CharSequence first, CharSequence second) {
        return first.toString() + second;
    }

    /**
     * 只读视图：四个读方法全部委托给外部实例，
     * 写侧后续 put / remove 对本视图可见（活视图，非快照）。
     * 仅能经 {@link StandardRadixTree#toReadView()} 获得。
     */
    private final class ReadView implements RadixTree<O> {

        @Override
        public O get(CharSequence key) {
            return StandardRadixTree.this.get(key);
        }

        @Override
        public boolean exist(CharSequence key) {
            return StandardRadixTree.this.exist(key);
        }

        @Override
        public Set<O> match(CharSequence key) {
            return StandardRadixTree.this.match(key);
        }

        @Override
        public O matchAny(CharSequence key) {
            return StandardRadixTree.this.matchAny(key);
        }
    }
}
