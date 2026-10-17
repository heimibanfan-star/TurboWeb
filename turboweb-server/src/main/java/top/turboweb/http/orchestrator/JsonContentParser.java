package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.rest.Http;
import top.turboweb.http.context.HttpJsonContent;
import top.turboweb.rest.ContentType;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * JSON 内容解析器：承接 {@code application/json} 请求体，产出 {@link HttpJsonContent}。
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
    public Http.Content parse(FullHttpRequest request, Charset defaultCharset) {
        return new HttpJsonContent(request, defaultCharset);
    }
}
