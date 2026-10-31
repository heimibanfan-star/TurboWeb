package top.turboweb.http.response;

import top.turboweb.http.core.ByteContent;
import top.turboweb.http.core.HttpResponseMessage;
import top.turboweb.http.core.StatusLine;

/**
 * 字节响应信息
 */
public class ByteResponseMessage extends HttpResponseMessage<ByteContent> {
    /**
     * 建立响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体内容，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public ByteResponseMessage(StatusLine statusLine, ByteContent content) {
        super(statusLine, content);
    }
}
