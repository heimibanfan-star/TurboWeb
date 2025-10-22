package top.turboweb.http.response;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.hc.core5.http.ContentType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import top.turboweb.commons.config.GlobalConfig;

import java.nio.charset.Charset;

/**
 * 支持reactor响应
 */
public class ReactorResponse <T> extends DefaultHttpResponse implements InternalCallResponse {

    public final Flux<T> bodyFlux;
    private final Charset charset;

    public ReactorResponse(Publisher<T> publisher) {
        this(publisher, ContentType.create("text/html", GlobalConfig.getResponseCharset()));
    }


    public ReactorResponse(Publisher<T> publisher, ContentType contentType) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.bodyFlux = Flux.from(publisher);
        this.charset = contentType.getCharset() == null ? GlobalConfig.getResponseCharset() : contentType.getCharset();
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType() + "; charset=" + charset);
    }

    /**
     * 设置响应内容类型
     *
     * @param contentType 内容类型
     */
    public void setContentType(ContentType contentType) {
        Charset charset = contentType.getCharset() == null ? GlobalConfig.getResponseCharset() : contentType.getCharset();
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType() + "; charset=" + charset);
    }

    /**
     * 获取响应体的Flux流
     *
     * @return flux流
     */
    public Flux<T> getFlux() {
        return bodyFlux;
    }

    /**
     * 获取响应体的字符集
     *
     * @return 字符集
     */
    public Charset getCharset() {
        return charset;
    }

    @Override
    public InternalCallType getType() {
        return InternalCallType.REACTOR;
    }
}
