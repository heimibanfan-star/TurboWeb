package top.turboweb.http.testkit;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * L1 用例的 Netty 请求夹具：只构造 {@link FullHttpRequest}，不碰通道、不起服务器、不写 socket。
 *
 * <p>{@link DefaultFullHttpRequest} 是引用计数对象，夹具产出的请求必须由调用方在
 * {@code finally} 里 {@link #release(FullHttpRequest)}，否则 Netty 的 leak detector 会告警。
 *
 * <p>GET 等无体请求使用 {@link Unpooled#EMPTY_BUFFER}（不可释放，release 是 no-op）；
 * 带体请求使用 {@code Unpooled.wrappedBuffer}，refCnt 从 1 起算。
 *
 * <p>消费者范围（R10）：当前仅 Task 6 的 {@code FullHttpContextTest} / {@code HttpContentTest}
 * 使用；后续任务若需手工请求夹具可直接复用。
 */
public final class Requests {

    /** urlencoded 表单：显式带 charset，避免依赖 GlobalConfig 当前值。 */
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";

    /** JSON：显式带 charset，避免依赖 GlobalConfig 当前值。 */
    public static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    /**
     * HEAD 夹具带的 Content-Type（D2，见任务报告）：HEAD 上下文的产品语义是"走 HttpContent.empty()，
     * 不读请求体类型"，夹具带类型头后，assertNull 的断言才真正区分 empty() 与 new HttpContent(request)——
     * 若 HEAD 分支被改坏，empty() 的 null 会变成头里的 "text/plain"。
     */
    private static final String HEAD_CONTENT_TYPE = "text/plain; charset=UTF-8";

    private Requests() {
    }

    public static FullHttpRequest get(String uri) {
        return build(HttpMethod.GET, uri, null, null, StandardCharsets.UTF_8);
    }

    public static FullHttpRequest getWithCookie(String uri, String cookieHeader) {
        FullHttpRequest request = get(uri);
        request.headers().set(HttpHeaderNames.COOKIE, cookieHeader);
        return request;
    }

    public static FullHttpRequest head(String uri) {
        return build(HttpMethod.HEAD, uri, HEAD_CONTENT_TYPE, null, StandardCharsets.UTF_8);
    }

    public static FullHttpRequest postForm(String uri, String formBody) {
        return build(HttpMethod.POST, uri, FORM_CONTENT_TYPE, formBody, StandardCharsets.UTF_8);
    }

    public static FullHttpRequest postJson(String uri, String json) {
        return build(HttpMethod.POST, uri, JSON_CONTENT_TYPE, json, StandardCharsets.UTF_8);
    }

    public static FullHttpRequest raw(HttpMethod method, String uri, String contentType, String body, Charset charset) {
        return build(method, uri, contentType, body, charset);
    }

    public static void release(FullHttpRequest request) {
        ReferenceCountUtil.release(request);
    }

    private static FullHttpRequest build(HttpMethod method, String uri, String contentType, String body, Charset charset) {
        ByteBuf content = body == null
                ? Unpooled.EMPTY_BUFFER
                : Unpooled.wrappedBuffer(body.getBytes(charset));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, content);
        if (contentType != null) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
        return request;
    }
}
