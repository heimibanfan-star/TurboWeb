package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
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
 * 基于 Reactor 的响应对象，用于支持异步流式响应。
 * <p>
 * 该响应对象可通过 {@link Flux} 异步推送 {@link ByteBuf} 数据到客户端，
 * 支持高并发、非阻塞的数据传输。
 * <p>
 * 特点：
 * <ul>
 *     <li>异步、非阻塞响应</li>
 *     <li>支持自定义内容类型和字符集</li>
 *     <li>实现 {@link InternalCallResponse}，类型为 {@link InternalCallResponse.InternalCallType#REACTOR}</li>
 * </ul>
 */
public class ReactorResponse extends DefaultHttpResponse implements InternalCallResponse {

    /** 响应体 Flux 流，用于异步推送数据 */
    public final Flux<ByteBuf> bodyFlux;

    /** 响应内容的字符集 */
    private final Charset charset;

    /**
     * 使用默认 HTML 内容类型创建 ReactorResponse。
     *
     * @param publisher 数据发布者 {@link Publisher}，提供异步 {@link ByteBuf} 数据
     */
    public ReactorResponse(Publisher<ByteBuf> publisher) {
        this(publisher, ContentType.create("text/html", GlobalConfig.getResponseCharset()));
    }

    /**
     * 使用自定义内容类型创建 ReactorResponse。
     *
     * @param publisher   数据发布者 {@link Publisher}，提供异步 {@link ByteBuf} 数据
     * @param contentType 内容类型 {@link ContentType}，若 charset 为空则使用全局默认字符集
     */
    public ReactorResponse(Publisher<ByteBuf> publisher, ContentType contentType) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.bodyFlux = Flux.from(publisher);
        this.charset = contentType.getCharset() == null ? GlobalConfig.getResponseCharset() : contentType.getCharset();
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
    }

    /**
     * 设置响应内容类型。
     *
     * @param contentType 内容类型 {@link ContentType}，会覆盖原有 Content-Type
     */
    public void setContentType(ContentType contentType) {
        this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
    }

    /**
     * 获取响应体的 Flux 流。
     *
     * @return 响应体 {@link Flux}，可异步订阅
     */
    public Flux<ByteBuf> getFlux() {
        return bodyFlux;
    }

    /**
     * 获取响应体的字符集。
     *
     * @return 响应字符集 {@link Charset}
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 返回内部调用类型。
     *
     * @return {@link InternalCallResponse.InternalCallType#REACTOR}
     */
    @Override
    public InternalCallType getType() {
        return InternalCallType.REACTOR;
    }
}
