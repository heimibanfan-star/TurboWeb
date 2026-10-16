package top.turboweb.http.context;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.core.cookie.DefaultHttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.DefaultHttpSession;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.testkit.Contexts;
import top.turboweb.http.testkit.Requests;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FullHttpContext} 的请求解析与内容能力契约通道（spec §5：context 行）。
 * bean 绑定语义由路由侧参数绑定器承载，其用例见参数解析器测试。
 *
 * <p>每条用例只断言可从返回值/异常观察的行为；请求夹具产出的 {@link FullHttpRequest}
 * 带引用计数，一律在 finally 里 {@link Requests#release(FullHttpRequest)}。
 */
class FullHttpContextTest {

    private static final JsonSerializer SERIALIZER = new JacksonJsonSerializer();
    private static final ConnectSession CONNECT_SESSION = new NoopConnectSession();

    private static HttpSession newSession() {
        return new DefaultHttpSession(new MemorySessionManager());
    }

    private static FullHttpContext context(FullHttpRequest request) {
        return context(request, newSession());
    }

    private static FullHttpContext context(FullHttpRequest request, HttpSession session) {
        return new FullHttpContext(Contexts.message(request, StandardCharsets.UTF_8),
                Contexts.content(request, StandardCharsets.UTF_8), session,
                new DefaultHttpCookies(request.headers()), CONNECT_SESSION, SERIALIZER, StandardCharsets.UTF_8);
    }

    // ---------- 构造器注入与基础 getter 面 ----------

    @Test
    void messageAndContentAreTheInjectedInstances() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpContext.Message message = Contexts.message(req, StandardCharsets.UTF_8);
            HttpContext.Content content = Contexts.content(req, StandardCharsets.UTF_8);
            FullHttpContext ctx = new FullHttpContext(message, content, newSession(),
                    new DefaultHttpCookies(req.headers()), CONNECT_SESSION, SERIALIZER, StandardCharsets.UTF_8);
            assertSame(message, ctx.message(), "上下文按注入就位，不另建契约对象");
            assertSame(content, ctx.content());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void messageExposesMethodAndPathOfTheRequest() {
        FullHttpRequest req = Requests.get("/x?a=1");
        try {
            FullHttpContext ctx = context(req);
            assertEquals("GET", ctx.message().method(), "请求方式取请求行方法字面量");
            assertEquals("/x", ctx.message().url().url(true), "仅地址形态截除查询段");
            assertEquals("/x?a=1", ctx.message().url().url(false));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nullMessageOrContentRejectedAtConstruction() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpContext.Message message = Contexts.message(req, StandardCharsets.UTF_8);
            HttpContext.Content content = Contexts.content(req, StandardCharsets.UTF_8);
            HttpSession session = newSession();
            NullPointerException e1 = assertThrows(NullPointerException.class, () -> new FullHttpContext(
                    null, content, session, new DefaultHttpCookies(req.headers()), CONNECT_SESSION, SERIALIZER,
                    StandardCharsets.UTF_8));
            assertEquals("message can not be null", e1.getMessage());
            NullPointerException e2 = assertThrows(NullPointerException.class, () -> new FullHttpContext(
                    message, null, session, new DefaultHttpCookies(req.headers()), CONNECT_SESSION, SERIALIZER,
                    StandardCharsets.UTF_8));
            assertEquals("content can not be null", e2.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void httpSessionAndConnectSessionAreTheInjectedInstances() {
        FullHttpRequest req = Requests.get("/x");
        HttpSession session = newSession();
        try {
            FullHttpContext ctx = context(req, session);
            assertSame(session, ctx.httpSession());
            assertSame(CONNECT_SESSION, ctx.getConnectSession());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void cookiesReadsTheCookieHeaderOfTheRequest() {
        FullHttpRequest req = Requests.getWithCookie("/x", "sid=abc; theme=dark");
        try {
            FullHttpContext ctx = context(req);
            assertEquals("abc", ctx.cookies().getCookie("sid"));
            assertEquals("dark", ctx.cookies().getCookie("theme"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void attachmentIsAWiredStableInstance() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ctx.attachment().attach("k", "v");
            assertEquals("v", ctx.attachment().getAttachment("k"), "同一实例才能读回");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void contentReflectsTheBodyTypeDispatchedByTheParser() {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest csv = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/x",
                "text/csv", "a,b", StandardCharsets.UTF_8);
        try {
            assertEquals(ContentType.APPLICATION_JSON, context(json).content().contentType(),
                    "JSON 体命中内置 JSON 解析器，内容侧类型就位");
            HttpContext.Content csvContent = context(csv).content();
            assertInstanceOf(HttpUnknowContent.class, csvContent, "无匹配解析器的类型回落未知内容，不解析体");
            assertEquals("text/csv", csvContent.contentType().getMimeType(), "未知内容仍携带头声明的类型");
        } finally {
            Requests.release(json);
            Requests.release(csv);
        }
    }

    @Test
    void createSseResponseAndEmitterCarryTheEventStreamContentType() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            SseResponse response = ctx.createSseResponse();
            // 产品码（SseResponse.setSseHeaders）只写媒体类型 "text/event-stream"，
            // charset 段由结果转化环节按缺省字符集补写，构造后的原始头不带编码段。
            assertEquals("text/event-stream",
                    response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            SseEmitter defaultEmitter = ctx.createSseEmitter();
            assertEquals("text/event-stream", defaultEmitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
            SseEmitter sizedEmitter = ctx.createSseEmitter(8);
            assertEquals("text/event-stream", sizedEmitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
        } finally {
            Requests.release(req);
        }
    }

    // ---------- FormContent / JsonContent 能力契约通道 ----------

    @Test
    void formChannelExposesUrlEncodedParamsViaContract() {
        FullHttpRequest req = Requests.postForm("/form", "name=alice&age=30");
        try {
            FullHttpContext ctx = context(req);
            HttpContext.FormContent formContent = assertInstanceOf(HttpContext.FormContent.class, ctx.content(),
                    "表单内容实现 FormContent 能力契约，参数封装权在内容侧");
            assertEquals(List.of("alice"), formContent.formParams().get("name"));
            assertEquals(List.of("30"), formContent.formParams().get("age"),
                    "字段值以多值列表原样呈现，折叠与绑定归绑定器");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonChannelExposesBodyStringViaContract() {
        FullHttpRequest req = Requests.postJson("/json", "{\"name\":\"alice\",\"age\":30}");
        try {
            FullHttpContext ctx = context(req);
            HttpContext.JsonContent jsonContent = assertInstanceOf(HttpContext.JsonContent.class, ctx.content(),
                    "JSON 内容实现 JsonContent 能力契约，字符串供数在内容侧");
            assertEquals("{\"name\":\"alice\",\"age\":30}", jsonContent.jsonString(),
                    "契约取回的就是体字符串，反序列化归绑定器");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void plainContentExposesNeitherFormNorJsonChannel() {
        FullHttpRequest req = Requests.get("/json");
        try {
            HttpContext.Content content = context(req).content();
            assertFalse(content instanceof HttpContext.FormContent, "未声明类型的未知内容不实现表单通道");
            assertFalse(content instanceof HttpContext.JsonContent, "未声明类型的未知内容不实现 JSON 通道");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void blankJsonBodyIsNormalizedToEmptyObjectViaContract() {
        FullHttpRequest req = Requests.postJson("/json", "");
        try {
            FullHttpContext ctx = context(req);
            // 内容装配期把空体规整成 "{}"，能力契约取回的字符串即规整结果
            HttpContext.JsonContent jsonContent = (HttpContext.JsonContent) ctx.content();
            assertEquals("{}", jsonContent.jsonString());
        } finally {
            Requests.release(req);
        }
    }

    // ---------- FormContent 文件归档通道 ----------

    @Test
    void multipartFormChannelArchivesUploadedField() {
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/up",
                "multipart/form-data; boundary=T",
                "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n",
                StandardCharsets.UTF_8);
        try {
            FullHttpContext ctx = context(req);
            HttpContext.FormContent formContent = assertInstanceOf(HttpContext.FormContent.class, ctx.content(),
                    "multipart 内容实现 FormContent 能力契约");
            assertEquals(1, formContent.formFiles().get("doc").size(), "multipart 文件段归档进 formFiles");
            assertNull(formContent.formFiles().get("absent"), "缺名不建键,空值归一由取用方决定");
            ctx.release();
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 编码（缺省编码由装配期判定、构造上下文时传入） ----------

    @Test
    void contextDefaultCharsetIsTheProvidedOne() {
        FullHttpRequest req = Requests.get("/json");
        try {
            FullHttpContext utf8Ctx = context(req);
            assertEquals(StandardCharsets.UTF_8, utf8Ctx.defaultCharset());
            Charset gbk = Charset.forName("GBK");
            FullHttpContext gbkCtx = new FullHttpContext(Contexts.message(req, gbk), Contexts.content(req, gbk),
                    newSession(), new DefaultHttpCookies(req.headers()), CONNECT_SESSION, SERIALIZER, gbk);
            assertEquals(Charset.forName("GBK"), gbkCtx.defaultCharset(),
                    "defaultCharset() 直接给出传入的缺省编码，不读请求头");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonCharsetComesFromTheContentTypeHeader() {
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/json",
                "application/json; charset=GBK", "{\"name\":\"张三\"}", Charset.forName("GBK"));
        try {
            // 缺省给 UTF-8，请求头声明 GBK 时请求体按 GBK 解码（经内容解析器与上下文全链路）
            assertEquals("{\"name\":\"张三\"}", ((HttpJsonContent) context(req).content()).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonCharsetWithoutHeaderFallsBackToContextDefault() {
        // 头不带 charset 段，按上下文缺省编码解码；取 ISO-8859-1 以区分于硬编码 UTF-8
        Charset charset = StandardCharsets.ISO_8859_1;
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/json",
                "application/json", "{\"name\":\"café\"}", charset);
        try {
            assertEquals("{\"name\":\"café\"}", new HttpJsonContent(req, charset).jsonString());
        } finally {
            Requests.release(req);
        }
    }

    // ---------- release 状态 ----------

    @Test
    void releaseIsRepeatableWhenContentHoldsNoResources() {
        FullHttpRequest req = Requests.postJson("/json", "{}");
        try {
            FullHttpContext ctx = context(req);
            assertDoesNotThrow(() -> {
                ctx.release();
                ctx.release();
            }, "JSON 内容不持有资源，release 恒为 no-op");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void releaseClosesFormContentAndIsRepeatable() {
        FullHttpRequest req = Requests.postForm("/form", "name=alice");
        try {
            FullHttpContext ctx = context(req);
            HttpFormContent content = (HttpFormContent) ctx.content();
            assertInstanceOf(HttpContext.AutoCloseContent.class, content, "表单内容持有解码器，走关闭通道");
            assertDoesNotThrow(() -> {
                content.formParams();
                ctx.release();
                ctx.release();
            }, "release 关闭持有的表单内容，重复调用幂等");
        } finally {
            Requests.release(req);
        }
    }

    /** 测试替身：L1 不需要真通道，ConnectSession 只是被存储/透传。 */
    private static final class NoopConnectSession implements ConnectSession {
        @Override
        public ChannelFuture send(String message) {
            return null;
        }

        @Override
        public void closeListener(Runnable runnable) {
        }

        @Override
        public void close() {
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }
    }
}
