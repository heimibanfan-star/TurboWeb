package top.turboweb.http.core.uri;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * URI 组件的百分号编解码原语。
 * <p>
 * 编码时按给定安全字集直通字符，其余字符按指定字符集编码为字节后
 * 输出 {@code %XY} 形态（十六进制大写）；解码时把 {@code %XY} 还原为
 * 字节序列后按指定字符集解码，无法识别的百分号序列原样保留。
 */
final class PercentCodec {

    /** RFC 3986 非保留字集：字母、数字与 {@code -._~} */
    static final BitSet UNRESERVED = new BitSet(256);

    /** URI 合法字集：非保留字集与子分隔符的并集 */
    static final BitSet URIC = new BitSet(256);

    private static final BitSet SUB_DELIMS = new BitSet(256);

    private static final int RADIX = 16;

    static {
        SUB_DELIMS.set('!');
        SUB_DELIMS.set('$');
        SUB_DELIMS.set('&');
        SUB_DELIMS.set('\'');
        SUB_DELIMS.set('(');
        SUB_DELIMS.set(')');
        SUB_DELIMS.set('*');
        SUB_DELIMS.set('+');
        SUB_DELIMS.set(',');
        SUB_DELIMS.set(';');
        SUB_DELIMS.set('=');

        for (int i = 'a'; i <= 'z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            UNRESERVED.set(i);
        }
        for (int i = '0'; i <= '9'; i++) {
            UNRESERVED.set(i);
        }
        UNRESERVED.set('-');
        UNRESERVED.set('.');
        UNRESERVED.set('_');
        UNRESERVED.set('~');
        URIC.or(SUB_DELIMS);
        URIC.or(UNRESERVED);
    }

    private PercentCodec() {
    }

    /**
     * 将内容按非保留字集编码后追加到缓冲。
     *
     * @param buf     目标缓冲
     * @param content 待编码内容，可为 {@code null}（不追加）
     * @param charset 字节编码字符集，{@code null} 时按 UTF-8
     */
    static void encode(final StringBuilder buf, final CharSequence content, final Charset charset) {
        encode(buf, content, charset, UNRESERVED);
    }

    /**
     * 将内容按给定安全字集编码后追加到缓冲。
     *
     * @param buf       目标缓冲
     * @param content   待编码内容，可为 {@code null}（不追加）
     * @param charset   字节编码字符集，{@code null} 时按 UTF-8
     * @param safechars 安全字集，命中的字符不经编码直通
     */
    static void encode(final StringBuilder buf, final CharSequence content, final Charset charset,
                       final BitSet safechars) {
        if (content == null) {
            return;
        }
        final CharBuffer cb = CharBuffer.wrap(content);
        final ByteBuffer bb = (charset != null ? charset : StandardCharsets.UTF_8).encode(cb);
        while (bb.hasRemaining()) {
            final int b = bb.get() & 0xff;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else {
                buf.append("%");
                final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
                final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
    }

    /**
     * 将内容按非保留字集编码为字符串。
     *
     * @param content 待编码内容
     * @param charset 字节编码字符集，{@code null} 时按 UTF-8
     * @return 编码结果；输入为 {@code null} 时返回 {@code null}
     */
    static String encode(final CharSequence content, final Charset charset) {
        if (content == null) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        encode(buf, content, charset, UNRESERVED);
        return buf.toString();
    }

    /**
     * 将内容按给定字符集解码，百分号序列还原为字节序列。
     *
     * @param content 待解码内容
     * @param charset 字节解码字符集，{@code null} 时按 UTF-8
     * @return 解码结果；输入为 {@code null} 时返回 {@code null}
     */
    static String decode(final CharSequence content, final Charset charset) {
        if (content == null) {
            return null;
        }
        final ByteBuffer bb = ByteBuffer.allocate(content.length());
        final CharBuffer cb = CharBuffer.wrap(content);
        while (cb.hasRemaining()) {
            final char c = cb.get();
            if (c == '%' && cb.remaining() >= 2) {
                final char uc = cb.get();
                final char lc = cb.get();
                final int u = Character.digit(uc, RADIX);
                final int l = Character.digit(lc, RADIX);
                if (u != -1 && l != -1) {
                    bb.put((byte) ((u << 4) + l));
                } else {
                    bb.put((byte) '%');
                    bb.put((byte) uc);
                    bb.put((byte) lc);
                }
            } else {
                bb.put((byte) c);
            }
        }
        bb.flip();
        return (charset != null ? charset : StandardCharsets.UTF_8).decode(bb).toString();
    }
}
