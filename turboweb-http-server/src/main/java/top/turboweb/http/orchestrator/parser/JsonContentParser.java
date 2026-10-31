package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.request.JsonRequestMessage;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * JSON 内容解析器：承接 {@code application/json} 请求体，产出 {@link JsonRequestMessage.JsonData}。
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
     * {@inheritDoc}
     */
    @Override
    public Object parse(FullHttpRequest request, Charset defaultCharset) {
        return new JsonRequestMessage.JsonData(request, defaultCharset);
    }
}
