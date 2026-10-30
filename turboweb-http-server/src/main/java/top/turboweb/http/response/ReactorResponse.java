package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.orchestrator.ReactorResponseWriter;
import top.turboweb.http.orchestrator.ResponseWriter;

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
 *     <li>实现 {@link ResponseCall}，声明由 {@link ReactorResponseWriter} 负责写出</li>
 * </ul>
 */
public class ReactorResponse extends DefaultHttpResponse implements ResponseCall {

    /** 响应体 Flux 流，用于异步推送数据 */
    public final Flux<ByteBuf> bodyFlux;

    /** 响应内容的字符集，随 {@link #setContentType(ContentType)} 同步更新；为 null 表示未显式指定，Content-Type 的 charset 段留待结果转化环节按缺省字符集补写 */
    private Charset charset;

    /**
     * 使用默认 HTML 内容类型创建 ReactorResponse；内容类型不带字符集，
     * charset 段留待结果转化环节按缺省字符集补写。
     *
     * @param publisher 数据发布者 {@link Publisher}，提供异步 {@link ByteBuf} 数据
     */
    public ReactorResponse(Publisher<ByteBuf> publisher) {
        this(publisher, ContentType.create("text/html"));
    }

    /**
     * 使用自定义内容类型创建 ReactorResponse。
     *
     * @param publisher   数据发布者 {@link Publisher}，提供异步 {@link ByteBuf} 数据
     * @param contentType 内容类型 {@link ContentType}；带字符集时 Content-Type 立即写明 charset 段，
     *                    不带时只写媒体类型，charset 段留待结果转化环节补写
     */
    public ReactorResponse(Publisher<ByteBuf> publisher, ContentType contentType) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.bodyFlux = Flux.from(publisher);
        writeContentType(contentType);
    }

    /**
     * 设置响应内容类型，并同步更新响应字符集。
     *
     * <p>写入 Content-Type 头的同时刷新 {@link #getCharset()} 的取值，
     * 二者始终一致；内容类型不带字符集时只写媒体类型，charset 段留待结果转化环节补写。</p>
     *
     * @param contentType 内容类型 {@link ContentType}，会覆盖原有 Content-Type
     */
    public void setContentType(ContentType contentType) {
        writeContentType(contentType);
    }

    /**
     * 按内容类型写出 charset 同步的 Content-Type 头：
     * 内容类型带字符集时写 "媒体类型;charset=字符集名"，不带时只写媒体类型。
     *
     * @param contentType 内容类型
     */
    private void writeContentType(ContentType contentType) {
        this.charset = contentType.getCharset();
        if (this.charset == null) {
            this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType());
        } else {
            this.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType.getMimeType() + ";" + "charset=" + charset.name());
        }
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
     * @return 响应字符集 {@link Charset}；为 null 表示未显式指定，由结果转化环节按缺省字符集补写
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     * 获取负责写出本响应的写出器类型。
     *
     * @return {@link ReactorResponseWriter} 的 Class 对象
     */
    @Override
    public Class<? extends ResponseWriter> getWriterType() {
        return ReactorResponseWriter.class;
    }
}
