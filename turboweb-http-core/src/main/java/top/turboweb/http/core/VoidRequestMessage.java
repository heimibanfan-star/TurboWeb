package top.turboweb.http.core;

/**
 * 无请求体的请求消息：请求头未声明内容类型、类型无匹配解析器或请求方式不携带请求体时
 * 兜底承载，不解析请求体，{@link #content()} 恒返回 {@code null}。
 *
 * <p>请求方式与请求目标由请求行承接；头部读写由 {@link AbstractHttpMessage} 承载。</p>
 */
public class VoidRequestMessage extends HttpRequestMessage<Void> {

    /**
     * 建立无请求体的请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @throws NullPointerException 当 {@code requestLine} 为 {@code null} 时抛出
     */
    public VoidRequestMessage(RequestLine requestLine) {
        super(requestLine);
    }
}
