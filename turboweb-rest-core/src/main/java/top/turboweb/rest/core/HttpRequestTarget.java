package top.turboweb.rest.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * http请求目标：存储请求行目标字段的原始字符串，提供目标原文、剥离查询参数后的
 * 路径与查询参数三个读取通道。
 *
 * <p>路径以 {@code /} 开头且结尾不携带斜杠（根路径保持 {@code /}）；
 * 查询参数按名分组、同名取值按出现顺序保留，无等号段（如 {@code ?flag}）取值为
 * {@code null}；百分号转义按 UTF-8 解码，转义畸形时抛出 {@link IllegalArgumentException}。</p>
 *
 * <p>目标原文构造期快照，路径每次现算，查询参数由首访 {@link #queryParams()} 时
 * 建立并缓存；本类按请求单线程使用，缓存建立不加同步。</p>
 */
public final class HttpRequestTarget {

    /** 目标原文快照 */
    private final String raw;

    /** 查询参数缓存：首个访问调用时建立 */
    private Map<String, List<String>> queryParams;

    /**
     * 从目标原文创建请求目标。
     *
     * @param raw 请求行目标字段的原始字符串，不能为null
     * @throws NullPointerException raw 为null
     */
    public HttpRequestTarget(String raw) {
        this.raw = Objects.requireNonNull(raw, "raw must not be null");
    }

    /**
     * 获取路径：目标原文自第一个 {@code ?} 或 {@code #} 处截断（不带查询参数），
     * 结尾斜杠归一剥离，根路径保持 {@code /}。
     *
     * @return 路径字符串
     */
    public String path() {
        int cut = raw.length();
        int queryIndex = raw.indexOf('?');
        if (queryIndex >= 0) {
            cut = queryIndex;
        }
        int fragmentIndex = raw.indexOf('#');
        if (fragmentIndex >= 0 && fragmentIndex < cut) {
            cut = fragmentIndex;
        }
        // 结尾斜杠归一剥离，根路径保持斜杠避免归一为空串
        int end = cut;
        while (end > 1 && raw.charAt(end - 1) == '/') {
            end--;
        }
        return raw.substring(0, end);
    }

    /**
     * 获取目标原文，不做任何改写。
     *
     * @return 请求行目标字段的原始字符串
     */
    public String raw() {
        return raw;
    }

    /**
     * 获取查询参数：解析目标原文的查询段并按名分组。
     *
     * @return 参数名到值列表的不可变映射；无查询段时返回空 Map
     * @throws IllegalArgumentException 查询段中存在不完整的百分号转义序列时抛出
     */
    public Map<String, List<String>> queryParams() {
        if (queryParams == null) {
            queryParams = parseQueryParams();
        }
        return queryParams;
    }

    /**
     * 解析查询段：取第一个 {@code ?} 之后、第一个 {@code #} 之前的查询串，
     * 按 {@code &} 切分为段并跳过空段；段内以第一个 {@code =} 拆分为名称与取值，
     * 无 {@code =} 的段取值为 {@code null}；名称与取值分别做百分号解码。
     */
    private Map<String, List<String>> parseQueryParams() {
        int markIndex = raw.indexOf('?');
        if (markIndex < 0) {
            return Map.of();
        }
        String query = raw.substring(markIndex + 1);
        int fragmentIndex = query.indexOf('#');
        if (fragmentIndex >= 0) {
            query = query.substring(0, fragmentIndex);
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String segment : query.split("&")) {
            if (segment.isEmpty()) {
                continue;
            }
            int equalsIndex = segment.indexOf('=');
            if (equalsIndex < 0) {
                grouped.computeIfAbsent(decode(segment), k -> new ArrayList<>(1)).add(null);
            } else {
                String name = decode(segment.substring(0, equalsIndex));
                String value = decode(segment.substring(equalsIndex + 1));
                grouped.computeIfAbsent(name, k -> new ArrayList<>(1)).add(value);
            }
        }
        // 外层映射与内层值列表各包不可变视图：值可为 null，不能用 List.copyOf
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        grouped.forEach((name, values) -> frozen.put(name, Collections.unmodifiableList(values)));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * 百分号解码：{@code %XX}（十六进制大小写均可）还原为字节并按 UTF-8 组装；
     * 其余字符保留字面值（含 {@code +}）。
     *
     * @throws IllegalArgumentException {@code %} 后不足两位或不是十六进制数字时抛出
     */
    private static String decode(String value) {
        int len = value.length();
        // 无转义序列的快路径：避免逐字符重建
        if (value.indexOf('%') < 0) {
            return value;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(len);
        int i = 0;
        while (i < len) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= len) {
                    throw new IllegalArgumentException("Malformed escape pair at index " + i + ": " + value);
                }
                int high = Character.digit(value.charAt(i + 1), 16);
                int low = Character.digit(value.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Malformed escape pair at index " + i + ": " + value);
                }
                bytes.write((high << 4) | low);
                i += 3;
            } else {
                bytes.write(c);
                i++;
            }
        }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
