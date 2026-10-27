package top.turboweb.rest.uri;

import java.util.Locale;

/**
 * URI 组件处理所需的字符序列判定与转换原语。
 */
final class URITextUtil {

    private URITextUtil() {
    }

    /**
     * 判断字符序列是否为 {@code null}、空串或全空白。
     *
     * @param s 待判定字符序列
     * @return 空白序列为 {@code true}
     */
    static boolean isBlank(final CharSequence s) {
        final int strLen = length(s);
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符序列是否为 {@code null} 或空串。
     *
     * @param s 待判定字符序列
     * @return 空串为 {@code true}
     */
    static boolean isEmpty(final CharSequence s) {
        return length(s) == 0;
    }

    /**
     * 获取字符序列长度，{@code null} 按 0 处理。
     *
     * @param cs 字符序列
     * @return 长度
     */
    static int length(final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }

    /**
     * 转为小写（按 Locale.ROOT，避免区域相关的大小写映射差异）。
     *
     * @param s 待转换字符串
     * @return 小写形式；输入为 {@code null} 时返回 {@code null}
     */
    static String toLowerCase(final String s) {
        if (s == null) {
            return null;
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断字符序列是否仅含 ASCII 字符。
     *
     * @param s 待判定字符序列，不能为 {@code null}
     * @return 全 ASCII 为 {@code true}
     */
    static boolean isAllASCII(final CharSequence s) {
        final int strLen = length(s);
        for (int i = 0; i < strLen; i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
