package top.turboweb.rest.core;

import java.util.Objects;

/**
 * http 请求消息：构造期持有请求行、地址与请求体内容，各访问器重复访问返回同一值。
 *
 * <p>请求方式由请求行承接；头部读写由 {@link AbstractHttpMessage} 承载，
 * 自带头存储在构造后即可写入与修改。</p>
 */
public class HttpRequestMessage extends AbstractHttpMessage<RequestLine, HttpContent> {

    /** 构造期持有的请求行 */
    private final RequestLine requestLine;

    /** 请求地址 */
    private final HttpUrl url;

    /** 请求体内容 */
    private final HttpContent content;

    /**
     * 建立请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param url         请求地址，不能为空
     * @param content     请求体内容，不能为空
     * @throws NullPointerException 当 {@code requestLine}、{@code url} 或 {@code content} 为 {@code null} 时抛出
     */
    public HttpRequestMessage(RequestLine requestLine, HttpUrl url, HttpContent content) {
        this.requestLine = Objects.requireNonNull(requestLine, "requestLine must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    /**
     * 获取请求方式。
     *
     * @return 请求方式
     */
    public HttpRequestMethod method() {
        return requestLine.method();
    }

    /**
     * 获取请求行。
     *
     * @return 请求行
     */
    public RequestLine requestLine() {
        return requestLine;
    }

    /**
     * 获取请求地址。
     *
     * @return 请求地址
     */
    public HttpUrl url() {
        return url;
    }

    /**
     * 获取请求体内容。
     *
     * @return 请求体内容
     */
    public HttpContent content() {
        return content;
    }
}
