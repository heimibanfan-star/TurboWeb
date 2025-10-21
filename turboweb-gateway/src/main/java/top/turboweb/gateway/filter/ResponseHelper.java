package top.turboweb.gateway.filter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;

import java.nio.charset.Charset;

/**
 * 网关响应助手接口。
 *
 * <p>定义统一的响应写出能力，用于在网关或业务过滤器中方便地构造并发送 HTTP 响应。
 * 通过该接口，调用方可以以一致的方式输出文本、HTML、JSON 或二进制内容。
 *
 * <p>每个 {@code ResponseHelper} 实例仅对应一次请求的响应周期，
 * 并应保证响应的幂等性：同一请求只能成功写出一次响应。
 * 当任意写出方法被调用成功后，{@link #isResponse()} 将返回 {@code true}。
 *
 * <h3>主要特性：</h3>
 * <ul>
 *   <li>统一封装多种响应类型的输出逻辑（文本、HTML、JSON、二进制）</li>
 *   <li>屏蔽底层 Netty 写出细节，提供一致的编程接口</li>
 *   <li>支持异步写出，所有方法均返回 {@link ChannelFuture}</li>
 *   <li>同一请求的响应只能写出一次，防止重复发送</li>
 * </ul>
 *
 * <p>默认实现为 {@link DefaultResponseHelper}。
 */
public interface ResponseHelper {

    /**
     * 判断当前请求是否已经产生响应。
     *
     * <p>当任意写入方法（如 {@link #writeText(String)} 或 {@link #writeJson(Object)}）
     * 被调用成功后，该方法应返回 {@code true}。
     *
     * @return 如果已发送响应则返回 {@code true}，否则返回 {@code false}
     */
    boolean isResponse();


    /**
     * 直接写入原始字节数据。
     *
     * <p>适用于返回自定义二进制数据（如文件下载、图片、流式数据等）。
     * 方法应自动设置 {@code Content-Type} 与 {@code Content-Length}。
     *
     * @param status      HTTP 状态码
     * @param buf         响应内容缓冲区
     * @param contentType 响应类型（包含 MIME 与编码信息）
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeByteBuf(HttpResponseStatus status, ByteBuf buf, ContentType contentType);

    /**
     * 写入纯文本响应。
     *
     * @param status  HTTP 状态码
     * @param text    文本内容
     * @param charset 字符集编码
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeText(HttpResponseStatus status, String text, Charset charset);

    /**
     * 写入纯文本响应（使用全局默认编码）。
     *
     * @param status HTTP 状态码
     * @param text   文本内容
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeText(HttpResponseStatus status, String text);

    /**
     * 写入 200 OK 状态的纯文本响应（使用全局默认编码）。
     *
     * @param text 文本内容
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeText(String text);

    /**
     * 写入 JSON 响应。
     *
     * <p>应将对象序列化为 JSON 字符串后写出，
     * 并自动设置 {@code Content-Type: application/json}。
     *
     * @param status  HTTP 状态码
     * @param json    待序列化对象
     * @param charset 输出字符集
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeJson(HttpResponseStatus status, Object json, Charset charset);

    /**
     * 写入 JSON 响应（使用全局默认编码）。
     *
     * @param status HTTP 状态码
     * @param json   待序列化对象
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeJson(HttpResponseStatus status, Object json);

    /**
     * 写入 200 OK 状态的 JSON 响应。
     *
     * @param json 待序列化对象
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeJson(Object json);

    /**
     * 写入 HTML 响应。
     *
     * @param status  HTTP 状态码
     * @param html    HTML 内容
     * @param charset 字符集编码
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeHtml(HttpResponseStatus status, String html, Charset charset);

    /**
     * 写入 HTML 响应（使用全局默认编码）。
     *
     * @param status HTTP 状态码
     * @param html   HTML 内容
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeHtml(HttpResponseStatus status, String html);

    /**
     * 写入 200 OK 状态的 HTML 响应。
     *
     * @param html HTML 内容
     * @return 异步写出结果的 {@link ChannelFuture}
     */
    ChannelFuture writeHtml(String html);
}
