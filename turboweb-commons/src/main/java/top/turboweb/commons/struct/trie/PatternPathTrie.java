package top.turboweb.commons.struct.trie;

import java.util.*;

/**
 * 支持多级通配符 '**'，单级通配符 '*'，路径参数 {name} 和 {name:type}（num,bool,str）
 * 的路径匹配前缀树实现（非线程安全，适合单线程或外部同步）。
 *
 * @param <V> 挂载内容类型
 */
public class PatternPathTrie<V> implements PathTrie<V> {

    private static final String WILDCARD_SINGLE = "*";
    private static final String WILDCARD_MULTI = "**";

    private final Node<V> root = new Node<>("");

    /**
     * 节点类型，支持普通路径段、单级通配符、多级通配符、参数段
     */
    private enum SegmentType {
        STATIC,          // 普通字符串
        WILDCARD_SINGLE, // '*'
        WILDCARD_MULTI,  // '**'
        PARAM            // {name} 或 {name:type}
    }

    private static class ParamInfo {
        final String name;
        final ParamType type;

        ParamInfo(String name, ParamType type) {
            this.name = name;
            this.type = type;
        }
    }

    private enum ParamType {
        STR, NUM, BOOL;

        static ParamType fromString(String s) {
            if (s == null) return STR;
            return switch (s.toLowerCase()) {
                case "num" -> NUM;
                case "bool" -> BOOL;
                case "str" -> STR;
                default -> throw new IllegalArgumentException("Unsupported param type: " + s);
            };
        }

        boolean match(String value) {
            return switch (this) {
                case NUM -> value.matches("^-?\\d+$");
                case BOOL -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                default -> true;
            };
        }
    }

    private static class Node<V> {
        String segment;
        SegmentType type;
        ParamInfo paramInfo; // 如果是参数节点，存信息
        Map<String, Node<V>> children = new LinkedHashMap<>();
        V value;

        Node(String segment) {
            this.segment = segment;
            this.type = parseSegmentType(segment);
            if (this.type == SegmentType.PARAM) {
                this.paramInfo = parseParam(segment);
            }
        }

        boolean hasValue() {
            return value != null;
        }

        private static SegmentType parseSegmentType(String seg) {
            if (seg.equals(WILDCARD_SINGLE)) return SegmentType.WILDCARD_SINGLE;
            if (seg.equals(WILDCARD_MULTI)) return SegmentType.WILDCARD_MULTI;
            if (seg.startsWith("{") && seg.endsWith("}")) return SegmentType.PARAM;
            return SegmentType.STATIC;
        }

        private static ParamInfo parseParam(String seg) {
            // 去除 {}
            String inner = seg.substring(1, seg.length() - 1);
            String[] parts = inner.split(":");
            String name = parts[0];
            ParamType type = ParamType.STR;
            if (parts.length > 1) {
                type = ParamType.fromString(parts[1]);
            }
            return new ParamInfo(name, type);
        }
    }

    @Override
    public void insert(String path, V value) {
        checkPath(path);
        String[] segments = splitPath(path);
        Node<V> cur = root;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (isParamSegment(seg)) {
                ParamInfo paramInfo = Node.parseParam(seg);
                // 规范化段字符串，补全类型为str（如果没有指定）
                if (!seg.contains(":")) {
                    seg = "{" + paramInfo.name + ":str}";
                    segments[i] = seg; // 同时修改数组中的元素，保证后续统一使用
                }
                // 同层参数冲突检查
                ParamInfo newParamInfo = Node.parseParam(seg);
                for (Node<V> child : cur.children.values()) {
                    if (child.type == SegmentType.PARAM) {
                        ParamInfo existParamInfo = child.paramInfo;
                        if (existParamInfo.type == newParamInfo.type
                                && !existParamInfo.name.equals(newParamInfo.name)) {
                            throw new IllegalArgumentException(
                                    "路径参数冲突：同一层不能有相同类型但不同名字的参数，已存在: {" +
                                            existParamInfo.name + ":" + existParamInfo.type.name().toLowerCase() +
                                            "}，新插入: " + seg);
                        }
                    }
                }
            }
            String key = keyForSegment(seg);
            cur.children.putIfAbsent(key, new Node<>(seg));
            cur = cur.children.get(key);
        }
        if (cur.hasValue()) {
            throw new IllegalArgumentException("路径已存在，无法重复插入: " + path);
        }
        cur.value = value;
    }



    @Override
    public Optional<MatchResult<V>> search(String path) {
        checkPath(path);
        String[] segments = splitPath(path);
        Map<String, String> params = new LinkedHashMap<>();
        Node<V> matched = searchNode(root, segments, 0, params);
        if (matched != null && matched.hasValue()) {
            return Optional.of(new MatchResult<>(matched.value, params));
        }
        return Optional.empty();
    }

    private Node<V> searchNode(Node<V> node, String[] segments, int idx, Map<String, String> params) {
        if (idx == segments.length) {
            if (node.hasValue()) {
                return node;
            }
            // 尝试多级通配符节点匹配0个层级
            Node<V> multiNode = node.children.get(WILDCARD_MULTI);
            if (multiNode != null && multiNode.hasValue()) {
                return multiNode;
            }
            return null;
        }

        String seg = segments[idx];

        // 1. 静态节点精确匹配
        Node<V> child = node.children.get(seg);
        if (child != null) {
            Node<V> res = searchNode(child, segments, idx + 1, params);
            if (res != null) return res;
        }

        // 2. 参数节点匹配，验证类型，参数名与值绑定
        for (Node<V> c : node.children.values()) {
            if (c.type == SegmentType.PARAM && c.paramInfo.type.match(seg)) {
                params.put(c.paramInfo.name, seg);
                Node<V> res = searchNode(c, segments, idx + 1, params);
                if (res != null) return res;
                params.remove(c.paramInfo.name);
            }
        }

        // 3. 单级通配符 '*'
        Node<V> starNode = node.children.get(WILDCARD_SINGLE);
        if (starNode != null) {
            Node<V> res = searchNode(starNode, segments, idx + 1, params);
            if (res != null) return res;
        }

        // 4. 多级通配符 '**'，匹配0个或多个
        Node<V> multiNode = node.children.get(WILDCARD_MULTI);
        if (multiNode != null) {
            // 1) 匹配0层
            Node<V> res = searchNode(multiNode, segments, idx, params);
            if (res != null) return res;

            // 2) 匹配1层及更多层，通过循环递归
            for (int i = idx + 1; i <= segments.length; i++) {
                res = searchNode(multiNode, segments, i, params);
                if (res != null) return res;
            }
        }
        return null;
    }

    @Override
    public boolean containsExact(String path) {
        checkPath(path);
        String[] segments = splitPath(path);
        Node<V> cur = root;
        for (String seg : segments) {
            String key = keyForSegment(seg);
            cur = cur.children.get(key);
            if (cur == null) return false;
        }
        return cur.hasValue();
    }

    @Override
    public boolean delete(String path) {
        checkPath(path);
        String[] segments = splitPath(path);
        return delete(root, segments, 0);
    }

    private boolean delete(Node<V> node, String[] segments, int idx) {
        if (idx == segments.length) {
            if (!node.hasValue()) return false;
            node.value = null;
            return node.children.isEmpty();
        }
        String key = keyForSegment(segments[idx]);
        Node<V> child = node.children.get(key);
        if (child == null) return false;
        boolean shouldRemove = delete(child, segments, idx + 1);
        if (shouldRemove) {
            node.children.remove(key);
            return !node.hasValue() && node.children.isEmpty();
        }
        return false;
    }

    @Override
    public Map<String, V> getEntriesWithPrefix(String prefix) {
        checkPrefix(prefix);
        String[] segments = splitPath(prefix);
        Node<V> cur = root;
        for (String seg : segments) {
            String key = keyForSegment(seg);
            cur = cur.children.get(key);
            if (cur == null) return Collections.emptyMap();
        }
        Map<String, V> result = new LinkedHashMap<>();
        collectEntries(cur, new StringBuilder(prefix.endsWith("/") ? prefix : prefix + "/"), result);
        return result;
    }

    private void collectEntries(Node<V> node, StringBuilder pathBuilder, Map<String, V> result) {
        if (node.hasValue()) {
            String path = pathBuilder.toString();
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            result.put(path, node.value);
        }
        for (Map.Entry<String, Node<V>> e : node.children.entrySet()) {
            int len = pathBuilder.length();
            pathBuilder.append(e.getKey()).append('/');
            collectEntries(e.getValue(), pathBuilder, result);
            pathBuilder.setLength(len);
        }
    }

    @Override
    public List<String> allPaths() {
        Map<String, V> all = getEntriesWithPrefix("");
        return new ArrayList<>(all.keySet());
    }

    private boolean isParamSegment(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }

    /**
     * keyForSegment 将参数段用类型作为 key 区分，保证同层不同类型参数可共存
     */
    private String keyForSegment(String seg) {
        if (seg.equals(WILDCARD_SINGLE) || seg.equals(WILDCARD_MULTI)) {
            return seg;
        }
        if (isParamSegment(seg)) {
            ParamInfo paramInfo = Node.parseParam(seg);
            // 用 {param:类型} 作为 key，忽略名字
            return "{param:" + paramInfo.type.name() + "}";
        }
        return seg;
    }

    private void checkPath(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("路径不能为空且必须以 '/' 开头: " + path);
        }
    }

    private void checkPrefix(String prefix) {
        if (prefix == null) throw new IllegalArgumentException("前缀不能为空");
        if (!prefix.isEmpty() && !prefix.startsWith("/")) {
            throw new IllegalArgumentException("前缀必须为空或以 '/' 开头");
        }
    }

    private String[] splitPath(String path) {
        String trim = path.startsWith("/") ? path.substring(1) : path;
        trim = trim.endsWith("/") ? trim.substring(0, trim.length() - 1) : trim;
        if (trim.isEmpty()) return new String[0];
        return trim.split("/");
    }

}
