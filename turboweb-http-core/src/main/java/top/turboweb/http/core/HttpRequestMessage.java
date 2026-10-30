package top.turboweb.http.core;

/**
 * http 请求消息：构造期持有请求行与请求体内容，各访问器重复访问返回同一值。
 *
 * <p>请求方式与请求目标（地址原文、路径与查询参数）由请求行承接；消息行、请求体内容
 * 与头部读写由 {@link AbstractHttpMessage} 承载。</p>
 */
public class HttpRequestMessage extends AbstractHttpMessage<RequestLine> {

    /**
     * 建立请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param content     请求体内容，不能为空
     * @throws NullPointerException 当 {@code requestLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public HttpRequestMessage(RequestLine requestLine, Content content) {
        super(requestLine, content);
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
