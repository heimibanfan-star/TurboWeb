package top.turboweb.commons.struct.trie;

import org.apache.tika.pipes.PipesResult;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

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

    /**
     * 默认参数匹配正则表达式
     */
    private static class REGEX_PATTERN {
        static final Pattern NUM = Pattern.compile("^-?\\d+(\\.\\d+)?$");
        static final Pattern INT = Pattern.compile("^-?\\d+$");
        static final Pattern DATE = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");
        static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");
    }

    /**
     * 参数类型
     */
    private final static class ParamType {
        static final ParamType STR = new ParamType("str");
        static final ParamType NUM = new ParamType("num");
        static final ParamType INT = new ParamType("int");
        static final ParamType BOOL = new ParamType("bool");
        static final ParamType DATE = new ParamType("date");
        static final ParamType IPV4 = new ParamType("ipv4");

        final String regex;
        final String name;

        /**
         * 匹配策略，用于参数值匹配
         */
        static final Map<ParamType, Function<String, Boolean>> STRATEGY = Map.of(
                NUM, value -> REGEX_PATTERN.NUM.matcher(value).matches(),
                INT, value -> REGEX_PATTERN.INT.matcher(value).matches(),
                BOOL, value -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value),
                DATE, value -> REGEX_PATTERN.DATE.matcher(value).matches(),
                IPV4, value -> REGEX_PATTERN.IPV4.matcher(value).matches(),
                STR, value -> true
        );

        ParamType(String name) {
            this.regex = null;
            this.name = name;
        }

        ParamType(String name, String regex) {
            Objects.requireNonNull(regex);
            this.regex = regex;
            this.name = name;
        }

        static ParamType fromString(String s) {
            if (s == null) return STR;
            return switch (s.toLowerCase()) {
                case "num" -> NUM;
                case "bool" -> BOOL;
                case "str" -> STR;
                case "int" -> INT;
                case "date" -> DATE;
                case "ipv4" -> IPV4;
                default -> {
                    if (s.startsWith("regex=")) {
                        s = s.substring(6);
                        yield new ParamType("regex", s);
                    }
                    throw new IllegalArgumentException("Unsupported param type: " + s);
                }
            };
        }

        boolean match(String value) {
            Function<String, Boolean> function = STRATEGY.get(this);
            if (function == null) {
                // 判断正则表达式是否是空
                if (this.regex == null) {
                    return false;
                }
                // 匹配
                return value.matches(this.regex);
            } else {
                return function.apply(value);
            }
        }

        String name() {
            return this.name;
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
    public Optional<MatchResult<V>> paramMatch(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("路径不能为空且必须以'/'开头: " + path);
        }
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

    @Override
    public Set<V> patternMatch(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("路径不能为空且必须以'/'开头: " + path);
        }
        String[] segments = splitPath(path);
        Set<V> result = new LinkedHashSet<>();
        matchAllValuesRecursive(root, segments, 0, result);
        return result;
    }

    private void matchAllValuesRecursive(Node<V> node, String[] segments, int idx, Set<V> result) {
        if (idx == segments.length) {
            if (node.hasValue()) {
                result.add(node.value);
            }
            Node<V> multiNode = node.children.get(WILDCARD_MULTI);
            if (multiNode != null && multiNode.hasValue()) {
                result.add(multiNode.value);
            }
            return;
        }

        String seg = segments[idx];

        // 1. 静态匹配
        Node<V> staticNode = node.children.get(seg);
        if (staticNode != null) {
            matchAllValuesRecursive(staticNode, segments, idx + 1, result);
        }

        // 2. 单级通配符 *
        Node<V> starNode = node.children.get(WILDCARD_SINGLE);
        if (starNode != null) {
            matchAllValuesRecursive(starNode, segments, idx + 1, result);
        }

        // 3. 多级通配符 **
        Node<V> multiNode = node.children.get(WILDCARD_MULTI);
        if (multiNode != null) {
            matchAllValuesRecursive(multiNode, segments, idx, result); // 匹配 0 层
            for (int i = idx + 1; i <= segments.length; i++) {
                matchAllValuesRecursive(multiNode, segments, i, result); // 匹配 1 层+
            }
        }
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
        // 判断**出现的次数
        int count = 0;
        for (int i = 0; i < path.length() - 1; i++) {
            if (path.charAt(i) == '*' && path.charAt(i + 1) == '*') {
                count++;
            }
        }
        if (count > 1) {
            throw new IllegalArgumentException("路径中**出现的次数不能超过1: " + path);
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
