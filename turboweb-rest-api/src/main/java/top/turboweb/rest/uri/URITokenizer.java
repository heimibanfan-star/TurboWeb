package top.turboweb.rest.uri;

/**
 * 面向 URI 组件解析的轻量分词器：在给定游标区间内按分隔符提取词元。
 * <p>
 * 词元提取语义：跳过前导空白，空白序列在输出中折叠为单个空格；
 * 空白为空格、水平制表、回车与换行四种字符。
 */
final class UriTokenizer {

    static final char CR = 13;
    static final char LF = 10;
    static final char SP = 32;
    static final char HT = 9;

    private UriTokenizer() {
    }

    /**
     * 分隔符判定：字符是否属于分隔符集合。
     */
    interface Delimiter {

        boolean test(char ch);
    }

    /**
     * 构造由若干字符组成的多字符分隔符判定。
     *
     * @param delimiters 分隔符字符，至少一个
     * @return 分隔符判定
     */
    static Delimiter delimiters(final char... delimiters) {
        return ch -> {
            for (final char delimiter : delimiters) {
                if (delimiter == ch) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 判断字符是否属于词元解析语义下的空白。
     *
     * @param ch 待判定字符
     * @return 空白为 {@code true}
     */
    static boolean isWhitespace(final char ch) {
        return ch == SP || ch == HT || ch == CR || ch == LF;
    }

    /**
     * 解析区间游标。
     */
    static final class Cursor {

        private final int lowerBound;
        private final int upperBound;
        private int pos;

        Cursor(final int lowerBound, final int upperBound) {
            if (lowerBound < 0) {
                throw new IllegalArgumentException("lowerBound is negative");
            }
            if (lowerBound > upperBound) {
                throw new IllegalArgumentException("lowerBound cannot be greater than upperBound");
            }
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.pos = lowerBound;
        }

        int getLowerBound() {
            return this.lowerBound;
        }

        int getUpperBound() {
            return this.upperBound;
        }

        int getPos() {
            return this.pos;
        }

        void updatePos(final int pos) {
            if (pos < this.lowerBound) {
                throw new IllegalStateException("pos: " + pos + " < lowerBound: " + this.lowerBound);
            }
            if (pos > this.upperBound) {
                throw new IllegalStateException("pos: " + pos + " > upperBound: " + this.upperBound);
            }
            this.pos = pos;
        }

        boolean atEnd() {
            return this.pos >= this.upperBound;
        }
    }

    /**
     * 从游标当前位置起提取词元：遇分隔符或空白停止，空白被跳过。
     *
     * @param buf      待解析字符序列
     * @param cursor   区间游标，解析后推进到停止位置
     * @param delim    分隔符判定，可为 {@code null}
     * @return 词元内容，不含分隔符与空白
     */
    static String parseContent(final CharSequence buf, final Cursor cursor, final Delimiter delim) {
        final StringBuilder dst = new StringBuilder();
        copyContent(buf, cursor, delim, dst);
        return dst.toString();
    }

    /**
     * 从游标当前位置起提取词元：空白语义生效——前导空白跳过，
     * 词元内部空白序列折叠为单个空格，遇分隔符停止。
     *
     * @param buf      待解析字符序列
     * @param cursor   区间游标，解析后推进到停止位置
     * @param delim    分隔符判定，可为 {@code null}
     * @return 词元内容
     */
    static String parseToken(final CharSequence buf, final Cursor cursor, final Delimiter delim) {
        final StringBuilder dst = new StringBuilder();
        boolean whitespace = false;
        while (!cursor.atEnd()) {
            final char current = buf.charAt(cursor.getPos());
            if (delim != null && delim.test(current)) {
                break;
            } else if (isWhitespace(current)) {
                skipWhiteSpace(buf, cursor);
                whitespace = true;
            } else {
                if (whitespace && dst.length() > 0) {
                    dst.append(' ');
                }
                copyContent(buf, cursor, delim, dst);
                whitespace = false;
            }
        }
        return dst.toString();
    }

    /**
     * 跳过游标当前位置起的连续空白。
     *
     * @param buf    待解析字符序列
     * @param cursor 区间游标
     */
    static void skipWhiteSpace(final CharSequence buf, final Cursor cursor) {
        int pos = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        for (int i = cursor.getPos(); i < indexTo; i++) {
            if (!isWhitespace(buf.charAt(i))) {
                break;
            }
            pos++;
        }
        cursor.updatePos(pos);
    }

    /**
     * 将游标当前位置起、直到分隔符或空白之前的内容追加到目标缓冲。
     *
     * @param buf    待解析字符序列
     * @param cursor 区间游标，解析后推进到停止位置
     * @param delim  分隔符判定，可为 {@code null}
     * @param dst    目标缓冲
     */
    static void copyContent(final CharSequence buf, final Cursor cursor, final Delimiter delim,
                            final StringBuilder dst) {
        int pos = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        for (int i = cursor.getPos(); i < indexTo; i++) {
            final char current = buf.charAt(i);
            if ((delim != null && delim.test(current)) || isWhitespace(current)) {
                break;
            }
            pos++;
            dst.append(current);
        }
        cursor.updatePos(pos);
    }
}
