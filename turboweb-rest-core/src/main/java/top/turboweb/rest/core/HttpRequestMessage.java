package top.turboweb.rest.core;

/**
 * http请求消息的读取契约：请求方式、请求行、请求地址与请求体内容的取用入口，
 * 并经 {@link HttpMessage} 平铺承载请求头的读取访问。
 *
 * <p>请求头读取访问由 {@link HttpMessage} 承载，头名查询大小写不敏感；
 * 头未发送时返回 {@code null}，仅 {@link #contentLength()} 未发送或取值无效时记 0。
 * 请求体侧声明的内容类型经 {@link #content()} 返回的 {@link HttpContent} 读取。</p>
 */
public interface HttpRequestMessage extends HttpMessage<RequestLine, HttpContent> {

    /**
     * 获取请求方式，取请求行的方法字面量。
     *
     * @return 请求方式
     */
    HttpRequestMethod method();

    /**
     * 获取请求行。
     *
     * @return 请求行，永不为 null
     */
    RequestLine requestLine();

    /**
     * 获取请求地址。
     *
     * @return 请求地址，永不为 null
     */
    HttpUrl url();

    /**
     * 获取请求体内容。
     *
     * @return 请求体内容，永不为 null
     */
    HttpContent content();

}
