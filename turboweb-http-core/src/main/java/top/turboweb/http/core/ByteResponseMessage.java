package top.turboweb.http.core;

/**
 * 字节数组响应消息：响应体以堆上字节数组承载，本类只做持有，各访问器重复访问返回同一值。
 *
 * <p>响应状态由状态行承接；头部读写由 {@link HttpResponseMessage} 的基类承载。</p>
 */
public final class ByteResponseMessage extends HttpResponseMessage<byte[]> {

    /**
     * 建立字节响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体字节数组，按入参原样持有，不作复制，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public ByteResponseMessage(StatusLine statusLine, byte[] content) {
        super(statusLine, content);
    }
}
