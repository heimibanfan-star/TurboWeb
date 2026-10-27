package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.http.context.HttpFormContent;
import top.turboweb.rest.core.ContentType;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * 表单内容解析器：同时承接 {@code application/x-www-form-urlencoded} 与
 * {@code multipart/form-data} 请求体，产出 {@link HttpFormContent}。
 */
public class FormContentParser implements HttpContentParser {

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<ContentType> contentTypes() {
        return Set.of(ContentType.APPLICATION_FORM_URLENCODED, ContentType.MULTIPART_FORM_DATA);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpContent parse(FullHttpRequest request, Charset defaultCharset) {
        return new HttpFormContent(request, defaultCharset);
    }
}
