package top.turboweb.http.core;

/**
 * http 响应消息：构造期持有状态行与响应体内容，各访问器重复访问返回同一值。
 *
 * <p>响应状态由状态行承接；消息行、响应体内容与头部读写由 {@link AbstractHttpMessage} 承载。</p>
 */
public class HttpResponseMessage extends AbstractHttpMessage<StatusLine> {

    /**
     * 建立响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体内容，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public HttpResponseMessage(StatusLine statusLine, Content content) {
        super(statusLine, content);
    }

    /**
     * 获取状态行。
     *
     * @return 状态行
     */
    public StatusLine statusLine() {
        return line();
    }
}
