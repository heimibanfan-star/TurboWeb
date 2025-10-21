package top.turboweb.gateway.filter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.http.ContentType;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JsonSerializer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认的 HTTP 响应处理器实现。
 *
 * <p>封装基于 Netty 的 HTTP 响应输出逻辑，提供便捷的响应写入方法：
 * 支持输出 <b>文本</b>、<b>JSON</b>、<b>HTML</b>、<b>二进制</b> 等多种内容类型。
 *
 * <p>每个 {@code DefaultResponseHelper} 实例与一个请求的
 * {@link ChannelHandlerContext} 绑定，用于安全地将业务结果写回客户端。
 * 内部通过 {@link AtomicBoolean} 确保同一请求的响应只会写出一次，防止重复发送。
 *
 * <h3>主要特性：</h3>
 * <ul>
 *   <li>支持多种内容类型输出（文本、HTML、JSON、二进制等）</li>
 *   <li>自动设置 Content-Type 与 Content-Length</li>
 *   <li>响应幂等性保障（防止重复写出）</li>
 *   <li>与 {@link JsonSerializer} 集成以便快速输出 JSON</li>
 * </ul>
 *
 * <p>通常不需要直接使用本类，框架会在内部通过 {@link ResponseHelper}
 * 接口自动适配。
 */
public class DefaultResponseHelper implements ResponseHelper{

    private final ChannelHandlerContext ctx;
    private final AtomicBoolean write = new AtomicBoolean(false);
    private final JsonSerializer jsonSerializer;

    /**
     * 构造默认响应助手。
     *
     * @param ctx            Netty 通道上下文
     * @param jsonSerializer JSON 序列化工具
     */
    public DefaultResponseHelper(ChannelHandlerContext ctx, JsonSerializer jsonSerializer) {
        this.ctx = ctx;
        this.jsonSerializer = jsonSerializer;
    }

    /**
     * 判断当前请求是否已写入响应。
     *
     * @return 如果已写入响应则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean isResponse() {
        return write.get();
    }

    /**
     * 将原始字节数据写入响应。
     *
     * <p>适用于二进制或自定义类型输出。方法会自动设置
     * {@code Content-Type} 和 {@code Content-Length}。
     *
     * @param status      HTTP 状态码，不能为空
     * @param buf         响应内容缓冲区（{@link ByteBuf}）
     * @param contentType 内容类型与编码信息
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    @Override
    public ChannelFuture writeByteBuf(HttpResponseStatus status, ByteBuf buf, ContentType contentType) {
        Objects.requireNonNull(status, "status can not be null");
        Objects.requireNonNull(buf, "buf can not be null");
        Objects.requireNonNull(contentType, "contentType can not be null");
        if (write.compareAndSet(false, true)) {
            Charset charset = contentType.getCharset();
            String cType = contentType.getMimeType() + ";" + (charset != null? "charset=" + charset.name() : "");
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, cType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
            return ctx.writeAndFlush(response);
        } else {
            ChannelPromise promise = ctx.newPromise();
            promise.setFailure(new IllegalStateException("response already written"));
            return promise;
        }
    }

    /**
     * 写入纯文本响应。
     *
     * @param status  HTTP 状态码
     * @param text    文本内容
     * @param charset 字符集编码
     */
    @Override
    public ChannelFuture writeText(HttpResponseStatus status, String text, Charset charset) {
        ByteBuf buf = ctx.alloc().buffer(text.length());
        buf.writeCharSequence(text, charset);
        ContentType contentType = ContentType.create(HttpHeaderValues.TEXT_HTML.toString(), charset.name());
        return writeByteBuf(status, buf, contentType);
    }

    /**
     * 写入纯文本响应（使用全局默认编码）。
     */
    @Override
    public ChannelFuture writeText(HttpResponseStatus status, String text) {
        return writeText(status, text, GlobalConfig.getResponseCharset());
    }

    /**
     * 写入 200 OK 状态的纯文本响应。
     */
    @Override
    public ChannelFuture writeText(String text) {
        return writeText(HttpResponseStatus.OK, text);
    }

    /**
     * 输出 JSON 格式响应。
     *
     * <p>该方法会使用 {@link JsonSerializer} 将对象序列化为字符串，
     * 并以 {@code application/json} 类型返回。
     *
     * @param status  HTTP 状态码
     * @param json    待序列化的对象
     * @param charset 输出编码
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    @Override
    public ChannelFuture writeJson(HttpResponseStatus status, Object json, Charset charset) {
        try {
            String bean = jsonSerializer.beanToJson(json);
            ByteBuf buf = ctx.alloc().buffer(bean.length());
            buf.writeCharSequence(bean, charset);
            ContentType contentType = ContentType.create(HttpHeaderValues.APPLICATION_JSON.toString(), charset.name());
            return writeByteBuf(status, buf, contentType);
        } catch (Exception e) {
            ChannelPromise promise = ctx.newPromise();
            promise.setFailure(e);
            return promise;
        }
    }

    /**
     * 写入 JSON 响应（使用全局默认编码）。
     */
    @Override
    public ChannelFuture writeJson(HttpResponseStatus status, Object json) {
        return writeJson(status, json, GlobalConfig.getResponseCharset());
    }

    /**
     * 写入 200 OK 状态的 JSON 响应。
     */
    @Override
    public ChannelFuture writeJson(Object json) {
        return writeJson(HttpResponseStatus.OK, json);
    }

    /**
     * 写入 HTML 响应。
     *
     * @param status  HTTP 状态码
     * @param html    HTML 内容
     * @param charset 字符编码
     */
    @Override
    public ChannelFuture writeHtml(HttpResponseStatus status, String html, Charset charset) {
        ByteBuf buf = ctx.alloc().buffer(html.length());
        buf.writeCharSequence(html, charset);
        ContentType contentType = ContentType.create(HttpHeaderValues.TEXT_HTML.toString(), charset.name());
        return writeByteBuf(status, buf, contentType);
    }

    /**
     * 写入 HTML 响应（使用全局默认编码）。
     */
    @Override
    public ChannelFuture writeHtml(HttpResponseStatus status, String html) {
        return writeHtml(status, html, GlobalConfig.getResponseCharset());
    }

    /**
     * 写入 200 OK 状态的 HTML 响应。
     */
    @Override
    public ChannelFuture writeHtml(String html) {
        return writeHtml(HttpResponseStatus.OK, html);
    }

}
