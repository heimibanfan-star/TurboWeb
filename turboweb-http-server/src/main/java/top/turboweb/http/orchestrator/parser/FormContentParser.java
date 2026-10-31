package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.request.FormRequestMessage;

import java.nio.charset.Charset;
import java.util.Set;

/**
 * 表单内容解析器：同时承接 {@code application/x-www-form-urlencoded} 与
 * {@code multipart/form-data} 请求体，产出 {@link FormRequestMessage.FormData}。
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
    public Object parse(FullHttpRequest request, Charset defaultCharset) {
        return new FormRequestMessage.FormData(request, defaultCharset);
    }
}
