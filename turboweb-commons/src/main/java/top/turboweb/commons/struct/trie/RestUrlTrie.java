package top.turboweb.commons.struct.trie;

import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 处理url的前缀树
 */
public class RestUrlTrie<T> extends UrlTrie<T, RestUrlTrie.MatchResult<T>> {

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

    /**
     * 参数信息
     */
    private static class ParamInfo  {
        final String name;
        final ParamType type;

        private ParamInfo(String name, ParamType type) {
            this.name = name;
            this.type = type;
        }
    }

    private static class ParamDetails extends Details {

        final ParamInfo paramInfo;

        ParamDetails(ParamInfo paramInfo, String newKey) {
            super(newKey);
            this.paramInfo = paramInfo;
        }
    }

    /**
     * 匹配的结果
     */
    public static class MatchResult<T> {
        public final T value;
        public final Map<String, String> params;

        private MatchResult(T value, Map<String, String> params) {
            this.value = value;
            // 封装为不可变map
            this.params = Collections.unmodifiableMap(params);
        }
    }

    @Override
    protected Details handleSubKey(String subKey) {
        if (!isParamSegment(subKey)) {
            return null;
        }
        ParamInfo paramInfo = parseParam(subKey);
        if (!subKey.contains(":")) {
            subKey = "{" + paramInfo.name + ":" + paramInfo.type.name() + "}";
        }
        return new ParamDetails(paramInfo, subKey);
    }

    /**
     * 解析参数片段
     * @param seg 参数片段
     * @return 参数信息
     */
    private ParamInfo parseParam(String seg) {
        // 去除{}
        String inner = seg.substring(1, seg.length() - 1);
        String[] parts = inner.split(":");
        String name = parts[0];
        ParamType type = ParamType.STR;
        if (parts.length > 1) {
            type = ParamType.fromString(parts[1]);
        }
        return new ParamInfo(name, type);
    }

    @Override
    public void insert(String key, T value, boolean overwrite) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        Set<String> paramNames = new HashSet<>();
        String[] parts = splitKey(key);

        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Empty path segment is not allowed: " + key);
            }

            // 占位符片段
            if (part.startsWith("{") && part.endsWith("}")) {
                String inner = part.substring(1, part.length() - 1).trim();

                // 检测非法的双冒号或空类型
                if (inner.contains("::") || inner.endsWith(":")) {
                    throw new IllegalArgumentException("Invalid parameter syntax in: " + part);
                }

                int colonIndex = inner.indexOf(':');
                String paramName = colonIndex > 0 ? inner.substring(0, colonIndex).trim() : inner;
                String typeName = colonIndex > 0 ? inner.substring(colonIndex + 1).trim() : null;

                // 检测参数名重复
                if (!paramNames.add(paramName)) {
                    throw new IllegalArgumentException("Duplicate path variable '" + paramName + "' in path: " + key);
                }

                // 检测参数名合法性
                if (!paramName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("Invalid path variable name: " + paramName);
                }

                // 检测类型是否支持
                if (typeName != null) {
                    try {
                        ParamType.fromString(typeName);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Unsupported parameter type '" + typeName + "' in path: " + key);
                    }
                }
            }
        }
        super.insert(key, value, overwrite);
    }




    private class Frame {
        Node<T> node;
        int index;
        Map<String, String> params;
        Iterator<Node<T>> iterator;

        Frame(Node<T> node, int index, Map<String, String> params, Iterator<Node<T>> iterator) {
            this.node = node;
            this.index = index;
            this.params = params;
            this.iterator = iterator;
        }
    }

    @Override
    protected MatchResult<T> doMatch(String[] segs) {
        Deque<Frame> stack = new ArrayDeque<>();
        Node<T> current = root;
        Map<String, String> params = new HashMap<>();
        int index = 0;

        while (true) {
            if (index == segs.length) {
                // 匹配结束
                if (current.value() != null) {
                    return new MatchResult<>(current.value(), params);
                }
                // 没有匹配值，尝试回溯
                if (stack.isEmpty()) return null;
                Frame back = stack.pop();
                current = back.node;
                index = back.index;
                params = back.params;
                continue;
            }

            String seg = segs[index];

            // --- 1. 先尝试固定匹配 ---
            Node<T> fixed = current.get(seg);
            if (fixed != null) {
                // 把可能的参数分支保存到回溯栈
                Map<String, Node<T>> subNodes = current.subNodes();
                if (subNodes != null) {
                    Iterator<Node<T>> it = subNodes.values().iterator();
                    List<Node<T>> candidates = new ArrayList<>();
                    while (it.hasNext()) {
                        Node<T> n = it.next();
                        ParamDetails details = n.details(ParamDetails.class);
                        if (details != null && details.paramInfo.type.match(seg)) {
                            candidates.add(n);
                        }
                    }
                    if (!candidates.isEmpty()) {
                        Iterator<Node<T>> it2 = candidates.iterator();
                        it2.next(); // 留一个用于当前匹配
                        if (it2.hasNext()) {
                            stack.push(new Frame(current, index, new HashMap<>(params), it2));
                        }
                    }
                }
                current = fixed;
                index++;
                continue;
            }

            // --- 2. 再尝试参数匹配 ---
            Map<String, Node<T>> subNodes = current.subNodes();
            if (subNodes == null || subNodes.isEmpty()) {
                // 匹配失败 -> 回溯
                if (stack.isEmpty()) return null;
                Frame back = stack.pop();
                current = back.node;
                index = back.index;
                params = back.params;
                continue;
            }

            // 找所有能匹配的参数节点
            List<Node<T>> candidates = new ArrayList<>();
            for (Node<T> n : subNodes.values()) {
                ParamDetails details = n.details(ParamDetails.class);
                if (details != null && details.paramInfo.type.match(seg)) {
                    candidates.add(n);
                }
            }

            if (candidates.isEmpty()) {
                // 当前层匹配不到，回溯
                if (stack.isEmpty()) return null;
                Frame back = stack.pop();
                current = back.node;
                index = back.index;
                params = back.params;
                continue;
            }

            // 保存除了第一个以外的候选，用于回溯
            Iterator<Node<T>> it = candidates.iterator();
            Node<T> first = it.next();
            if (it.hasNext()) {
                stack.push(new Frame(current, index, new HashMap<>(params), it));
            }

            // 应用第一个匹配
            ParamDetails details = first.details(ParamDetails.class);
            params.put(details.paramInfo.name, seg);
            current = first;
            index++;
        }
    }



    /**
     * 判断是否是参数片段
     * @param seg 片段
     * @return true是参数片段
     */
    private boolean isParamSegment(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }
}
