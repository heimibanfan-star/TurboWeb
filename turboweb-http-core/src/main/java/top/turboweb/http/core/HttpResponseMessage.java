package top.turboweb.http.core;

/**
 * http 响应消息：构造期持有状态行与响应体内容，各访问器重复访问返回同一值。
 *
 * <p>响应状态由状态行承接；消息行、响应体内容与头部读写由 {@link AbstractHttpMessage} 承载。
 * 响应体内容的类型由泛型实参填充，无响应体的消息以 {@link Void} 填充，
 * {@link #content()} 恒返回 {@code null}。</p>
 *
 * @param <CONTENT> 响应体内容的类型
 */
public class HttpResponseMessage<CONTENT> extends AbstractHttpMessage<StatusLine, CONTENT> {

    /**
     * 建立携带响应体内容的响应消息。
     *
     * @param statusLine 状态行，不能为空
     * @param content    响应体内容，不能为空
     * @throws NullPointerException 当 {@code statusLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public HttpResponseMessage(StatusLine statusLine, CONTENT content) {
        super(statusLine, content);
    }

    /**
     * 建立无响应体的响应消息，供无体消息类继承使用；
     * 业务侧建立无体响应请使用 {@link VoidResponseMessage}。
     *
     * @param statusLine 状态行，不能为空
     * @throws NullPointerException 当 {@code statusLine} 为 {@code null} 时抛出
     */
    protected HttpResponseMessage(StatusLine statusLine) {
        super(statusLine);
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
