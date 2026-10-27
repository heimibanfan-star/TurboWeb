package top.turboweb.http.core.helper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@code UrlHelper} 提供 URL 字符串的查询参数解析能力。
 * <p>
 * 解码口径为<b>纯百分号解码</b>（RFC 3986）：仅 {@code %XX} 序列被还原为字节再按
 * 字符集组装（默认 UTF-8，可显式指定其它字符集），加号 {@code +} 保持字面值、
 * 不视为空格。
 */
public final class UrlHelper {

    private UrlHelper() {
    }

    /**
     * 解析 URL 中的查询参数，转义字节按默认 UTF-8 组装。
     *
     * @param uri URL 字符串，不能为空
     * @return 按出现顺序排列的参数项列表
     * @throws NullPointerException     {@code uri} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 查询段中存在不完整的百分号转义序列时抛出
     */
    public static List<ParamPair> parseQueryParams(String uri) {
        return parseQueryParams(uri, StandardCharsets.UTF_8);
    }

    /**
     * 解析 URL 中的查询参数，转义字节按给定字符集组装。
     * <p>
     * 规则：取第一个 {@code ?} 之后、第一个 {@code #} 之前的查询串，按 {@code &}
     * 切分为段并跳过空段；段内以第一个 {@code =} 拆分为名称与取值，无 {@code =} 的段
     * 取值为 {@code null}；名称与取值分别做百分号解码。无查询串时返回空列表。
     *
     * @param uri     URL 字符串，不能为空
     * @param charset 字节组装字符集，不能为空
     * @return 按出现顺序排列的参数项列表
     * @throws NullPointerException     {@code uri} 或 {@code charset} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 查询段中存在不完整的百分号转义序列时抛出
     */
    public static List<ParamPair> parseQueryParams(String uri, Charset charset) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(charset, "charset");
        int markIndex = uri.indexOf('?');
        if (markIndex < 0) {
            return Collections.emptyList();
        }
        String query = uri.substring(markIndex + 1);
        int fragmentIndex = query.indexOf('#');
        if (fragmentIndex >= 0) {
            query = query.substring(0, fragmentIndex);
        }
        List<ParamPair> params = new ArrayList<>();
        for (String segment : query.split("&")) {
            if (segment.isEmpty()) {
                continue;
            }
            int equalsIndex = segment.indexOf('=');
            if (equalsIndex < 0) {
                params.add(new ParamPair(decode(segment, charset), null));
            } else {
                String name = decode(segment.substring(0, equalsIndex), charset);
                String value = decode(segment.substring(equalsIndex + 1), charset);
                params.add(new ParamPair(name, value));
            }
        }
        return params;
    }

    /**
     * 百分号解码：{@code %XX}（十六进制大小写均可）还原为字节并按 {@code charset}
     * 组装；其余字符保留字面值（含 {@code +}，裸非 ASCII 字符亦按 {@code charset}
     * 入字节通道往返）。
     *
     * @param value   待解码片段
     * @param charset 字节组装字符集
     * @return 解码后的字符串
     * @throws IllegalArgumentException {@code %} 后不足两位或不是十六进制数字时抛出
     */
    private static String decode(String value, Charset charset) {
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
            } else if (c < 0x80) {
                bytes.write(c);
                i++;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(charset);
                bytes.write(encoded, 0, encoded.length);
                i++;
            }
        }
        return bytes.toString(charset);
    }

    /**
     * {@code ParamPair} 是一个名称与取值的配对，承载查询串解析出的单个参数项。
     * <p>
     * 值允许为 {@code null}，表示查询段中未出现 {@code =}（如 {@code ?flag}）。
     */
    public static final class ParamPair {

        private final String name;
        private final String value;

        public ParamPair(String name, String value) {
            this.name = name;
            this.value = value;
        }

        /**
         * 获取名称（已按百分号解码）。
         *
         * @return 名称，可为空串（如 {@code ?=v}）
         */
        public String getName() {
            return name;
        }

        /**
         * 获取取值（已按百分号解码）。
         *
         * @return 取值；查询段不含 {@code =} 时为 {@code null}
         */
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }
}
