package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.exception.RequestParseException;

import java.nio.charset.Charset;
import java.util.Objects;
import java.util.Set;

/**
 * JSON 内容解析器：承接 {@code application/json} 请求体，解析产出 JSON 字符串。
 * Content-Type 必须 JSON，空白请求体补 {@code {}}，非空体去首尾空白后必须为
 * 花括号包裹的 JSON 对象，否则抛出 {@link RequestParseException}。
 */
public class JsonContentParser implements HttpContentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<ContentType> contentTypes() {
        return Set.of(ContentType.APPLICATION_JSON);
    }

    /**
     * 解析 JSON 请求体为字符串。
     *
     * @param request        JSON 请求对象，不能为空；Content-Type 非 JSON 或请求体无效时拒绝
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码，不能为空
     * @return 去空白、空体已补 {@code {}} 的 JSON 请求体字符串
     * @throws NullPointerException  当 {@code request} 或 {@code defaultCharset} 为 {@code null} 时抛出
     * @throws RequestParseException 当 Content-Type 不是 {@code application/json}，
     *                               或请求体不是花括号包裹的 JSON 对象时抛出
     */
    @Override
    public Object parse(FullHttpRequest request, Charset defaultCharset) {
        Objects.requireNonNull(request, "request can not be null");
        Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
        String mimeType = HttpRequestHelper.getContentType(request);
        if (!HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(mimeType)) {
            throw new RequestParseException("contentType is not application/json");
        }
        String body = request.content().toString(HttpRequestHelper.getRequestCharset(request, defaultCharset));
        if (body.isBlank()) {
            return "{}";
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new RequestParseException("The request body is not a valid json");
        }
        return trimmed;
    }
}
