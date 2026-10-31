package top.turboweb.http.core;

/**
 * 无响应体的响应消息：响应只由状态行与头部构成（如 204、304 语义），
 * {@link #content()} 恒返回 {@code null}。
 *
 * <p>响应状态由状态行承接；头部读写由 {@link AbstractHttpMessage} 承载。</p>
 */
public class VoidResponseMessage extends HttpResponseMessage<Void> {

    /**
     * 建立无响应体的响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @throws NullPointerException 当 {@code statusLine} 为 {@code null} 时抛出
     */
    public VoidResponseMessage(StatusLine statusLine) {
        super(statusLine);
    }
}
