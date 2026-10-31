package top.turboweb.http.response;

import top.turboweb.http.core.ByteContent;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;

/**
 * 标准响应消息：响应体以 {@link ByteContent} 承载的 {@link HttpResponseMessage} 实现，
 * 供标准写出通道按字节直接写出。
 */
public class StandardResponseMessage extends HttpResponseMessage<ByteContent> {

    /**
     * 建立标准响应消息，响应体包装为字节数组内容持有。
     *
     * @param statusLine 状态行，不能为空
     * @param bytes      响应体字节数组，包装为 {@link ByteContent} 按入参原样持有，不作复制，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code bytes} 为 {@code null} 时抛出
     */
    public StandardResponseMessage(StatusLine statusLine, byte[] bytes) {
        super(statusLine, new ByteContent(bytes));
    }

    /**
     * 建立标准响应消息，直接持有给定的字节数组内容。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体字节数组内容，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public StandardResponseMessage(StatusLine statusLine, ByteContent content) {
        super(statusLine, content);
    }
}
