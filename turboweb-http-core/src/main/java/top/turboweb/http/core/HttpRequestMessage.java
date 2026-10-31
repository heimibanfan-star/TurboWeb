package top.turboweb.http.core;

/**
 * http 请求消息：构造期持有请求行与请求体内容，各访问器重复访问返回同一值。
 *
 * <p>请求方式与请求目标（地址原文、路径与查询参数）由请求行承接；消息行、请求体内容
 * 与头部读写由 {@link AbstractHttpMessage} 承载。请求体内容的类型由泛型实参填充，
 * 无请求体的消息以 {@link Void} 填充，{@link #content()} 恒返回 {@code null}。</p>
 *
 * @param <CONTENT> 请求体内容的类型
 */
public class HttpRequestMessage<CONTENT> extends AbstractHttpMessage<RequestLine, CONTENT> {

    /**
     * 建立携带请求体内容的请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param content     请求体内容，不能为空
     * @throws NullPointerException 当 {@code requestLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public HttpRequestMessage(RequestLine requestLine, CONTENT content) {
        super(requestLine, content);
    }

    /**
     * 建立无请求体的请求消息，供无体消息类继承使用；
     * 业务侧建立无体请求请使用 {@link VoidRequestMessage}。
     *
     * @param requestLine 请求行，不能为空
     * @throws NullPointerException 当 {@code requestLine} 为 {@code null} 时抛出
     */
    protected HttpRequestMessage(RequestLine requestLine) {
        super(requestLine);
    }

    /**
     * 获取请求方式。
     *
     * @return 请求方式
     */
    public HttpRequestMethod method() {
        return requestLine().method();
    }

    /**
     * 获取请求行。
     *
     * @return 请求行
     */
    public RequestLine requestLine() {
        return line();
    }
}
