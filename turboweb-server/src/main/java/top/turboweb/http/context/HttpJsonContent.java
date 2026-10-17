package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import top.turboweb.rest.Http;
import top.turboweb.rest.ContentType;
import top.turboweb.http.core.helper.HttpRequestHelper;
import top.turboweb.http.exception.RequestParseException;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * {@code HttpJsonContent} 在构造期从请求自解析并持有 JSON 请求体字符串：
 * Content-Type 必须为 {@code application/json}，空白请求体补 {@code {}}，
 * 非空体去首尾空白后必须为花括号包裹的 JSON 对象，否则抛出 {@link RequestParseException}。
 *
 * <p>解码字符集按请求头 Content-Type 声明优先、未声明时回落到构造传入的缺省字符集，
 * 与请求体其余解码口径一致。
 *
 * <p>本类不持有请求对象的引用，体字节在构造期即复制为字符串；构造返回后调用方可立即释放请求。
 * 实现 {@link HttpContext.JsonContent} 能力契约（扩展 {@link Http.Content}）：
 * 字符串是堆上对象，生命周期由 GC 管理，
 * 无需要归还的原生内存，因此不背负关闭义务。
 */
public class HttpJsonContent implements HttpContext.JsonContent {

    private final String jsonString;

    /**
     * 从请求即时解析 JSON 请求体。
     *
     * @param request        JSON 请求对象，不能为空；Content-Type 非 JSON 或请求体无效时本构造拒绝
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码，不能为空
     * @throws NullPointerException  当 {@code request} 或 {@code defaultCharset} 为 {@code null} 时抛出
     * @throws RequestParseException 当 Content-Type 不是 {@code application/json}，
     *                               或请求体不是花括号包裹的 JSON 对象时抛出
     */
    public HttpJsonContent(FullHttpRequest request, Charset defaultCharset) {
        Objects.requireNonNull(request, "request can not be null");
        Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
        String mimeType = HttpRequestHelper.getContentType(request);
        if (!HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(mimeType)) {
            throw new RequestParseException("contentType is not application/json");
        }
        String body = request.content().toString(HttpRequestHelper.getRequestCharset(request, defaultCharset));
        if (body.isBlank()) {
            this.jsonString = "{}";
        } else {
            String trimmed = body.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                throw new RequestParseException("The request body is not a valid json");
            }
            this.jsonString = trimmed;
        }
    }

    /**
     * 获取构造期解析持有的 JSON 请求体字符串。
     *
     * @return 去空白、空体已补 {@code {}} 的字符串本身
     */
    @Override
    public String jsonString() {
        return this.jsonString;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentType contentType() {
        return ContentType.APPLICATION_JSON;
    }
}
