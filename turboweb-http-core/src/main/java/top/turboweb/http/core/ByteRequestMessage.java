package top.turboweb.http.core;

/**
 * 字节数组请求消息：请求体以堆上字节数组承载，本类只做持有，各访问器重复访问返回同一值。
 *
 * <p>请求方式与请求目标由请求行承接；头部读写由 {@link HttpRequestMessage} 的基类承载。</p>
 */
public final class ByteRequestMessage extends HttpRequestMessage<byte[]> {

    /**
     * 建立字节请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param content     请求体字节数组，按入参原样持有，不作复制，不能为空
     * @throws NullPointerException 当 {@code requestLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public ByteRequestMessage(RequestLine requestLine, byte[] content) {
        super(requestLine, content);
    }
}
