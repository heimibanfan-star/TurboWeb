package top.turboweb.http.response;

import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;

/**
 * 基于流式传输的response对象
 */
public class StreamResponse <T> extends DefaultHttpResponse implements InternalCallResponse {

    private final Flux<T> flux;
    private final ContentType contentType;

    public StreamResponse(Flux<T> flux) {
        this(flux, ContentType.create("text/html", GlobalConfig.getResponseCharset()));
    }

    public StreamResponse(Flux<T> flux, ContentType contentType) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.flux = flux;
        initHeader(contentType);
        this.contentType = contentType;
    }

    private void initHeader(ContentType contentType) {
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
        this.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        this.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
    }

    public Flux<T> getFlux() {
        return flux;
    }

    public ContentType getContentType() {
        return contentType;
    }

    @Override
    public InternalCallType getType() {
        return InternalCallType.STREAM;
    }
}
