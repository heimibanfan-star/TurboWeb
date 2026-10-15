package top.turboweb.http.context;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.content.HttpContent;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.DefaultHttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.DefaultHttpSession;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.testkit.Requests;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FullHttpContext} 的请求解析与参数绑定（spec §5：context 行）。
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
        return new FullHttpContext(request, session,
                new DefaultHttpCookies(request.headers()), CONNECT_SESSION, SERIALIZER, StandardCharsets.UTF_8);
    }

    // ---------- 构造器注入与基础 getter 面 ----------

    @Test
    void getRequestReturnsTheSameInstance() {
        FullHttpRequest req = Requests.get("/x");
        try {
            assertSame(req, context(req).getRequest());
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
    void headRequestGetsEmptyContentWhileGetGetsParsedContent() {
        FullHttpRequest head = Requests.head("/x");
        FullHttpRequest json = Requests.postJson("/x", "{}");
        try {
            assertNull(context(head).httpContent.getContentType(),
                    "HEAD 走 HttpContent.empty()：无 Content-Type");
            assertEquals("application/json", context(json).httpContent.getContentType(),
                    "非 HEAD 走 new HttpContent(request, defaultCharset)：Content-Type 已解析");
        } finally {
            Requests.release(head);
            Requests.release(json);
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

    // ---------- bindBean / loadForm / loadJson 绑定 ----------

    @Test
    void bindBeanCollapsesSingleAndKeepsMultiValues() {
        FullHttpRequest req = Requests.get("/x");
        try {
            Map<String, List<String>> params = new HashMap<>();
            params.put("name", List.of("alice"));
            params.put("age", List.of("30"));
            params.put("tag", List.of("a", "b"));
            QueryBean bean = context(req).bindBean(params, QueryBean.class);
            assertEquals("alice", bean.getName());
            assertEquals(30, bean.getAge());
            assertEquals(List.of("a", "b"), bean.getTag(), "多值留列表交 Jackson");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void bindBeanPassesNullValuedFlagThrough() {
        FullHttpRequest req = Requests.get("/x");
        try {
            Map<String, List<String>> params = new HashMap<>();
            params.put("name", Collections.singletonList(null));
            QueryBean bean = context(req).bindBean(params, QueryBean.class);
            assertNull(bean.getName(), "?flag 型 null 值折叠后原样传递,Jackson 置属性为 null");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void bindBeanOnEmptyMapYieldsEmptyBean() {
        FullHttpRequest req = Requests.get("/x");
        try {
            QueryBean bean = context(req).bindBean(Map.of(), QueryBean.class);
            assertNull(bean.getName());
            assertEquals(0, bean.getAge());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadFormMapsTheUrlEncodedBody() {
        FullHttpRequest req = Requests.postForm("/form", "name=alice&age=30");
        try {
            QueryBean bean = context(req).loadForm(QueryBean.class);
            assertEquals("alice", bean.getName());
            assertEquals(30, bean.getAge());
            assertNull(bean.getTag());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadJsonMapsTheJsonBody() {
        FullHttpRequest req = Requests.postJson("/json", "{\"name\":\"alice\",\"age\":30}");
        try {
            QueryBean bean = context(req).loadJson(QueryBean.class);
            assertEquals("alice", bean.getName());
            assertEquals(30, bean.getAge());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadJsonWithoutJsonContentTypeThrows() {
        FullHttpRequest req = Requests.get("/json");
        try {
            FullHttpContext ctx = context(req);
            RequestParseException ex =
                    assertThrows(RequestParseException.class, () -> ctx.loadJson(QueryBean.class));
            assertEquals("contentType is not application/json", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadJsonOnBlankBodyYieldsEmptyBean() {
        FullHttpRequest req = Requests.postJson("/json", "");
        try {
            // getJsonContent 把空体规整成 "{}"，因此不会抛 "JSON request body is empty"
            QueryBean bean = context(req).loadJson(QueryBean.class);
            assertNull(bean.getName());
            assertEquals(0, bean.getAge());
        } finally {
            Requests.release(req);
        }
    }

    // ---------- loadFile / loadFiles 上传通道 ----------

    @Test
    void loadFileReturnsFirstUploadOfMultipartField() {
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/up",
                "multipart/form-data; boundary=T",
                "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n",
                StandardCharsets.UTF_8);
        try {
            FullHttpContext ctx = context(req);
            assertEquals(1, ctx.loadFiles("doc").size(), "multipart 文件段归档进 formFiles");
            assertSame(ctx.loadFiles("doc").getFirst(), ctx.loadFile("doc"), "单文件通道返回该字段首文件");
            ctx.release();
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadFilesOnMissingFieldReturnsEmptyList() {
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/up",
                "multipart/form-data; boundary=T",
                "--T\r\nContent-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\nhello\r\n--T--\r\n",
                StandardCharsets.UTF_8);
        try {
            FullHttpContext ctx = context(req);
            assertEquals(List.of(), ctx.loadFiles("absent"), "字段不存在按契约归一为空集合,不交 null");
            assertNull(ctx.loadFile("absent"), "单文件通道未命中契约本就是 null");
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
            FullHttpContext gbkCtx = new FullHttpContext(req, newSession(),
                    new DefaultHttpCookies(req.headers()), CONNECT_SESSION, SERIALIZER, Charset.forName("GBK"));
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
            // 缺省给 UTF-8，请求头声明 GBK 时请求体按 GBK 解码
            assertEquals("{\"name\":\"张三\"}", new HttpContent(req, StandardCharsets.UTF_8).getJsonContent());
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
            assertEquals("{\"name\":\"café\"}", new HttpContent(req, charset).getJsonContent());
        } finally {
            Requests.release(req);
        }
    }

    // ---------- release 状态 ----------

    @Test
    void releaseIsRepeatableWhenNothingWasParsed() {
        FullHttpRequest req = Requests.postJson("/json", "{}");
        try {
            FullHttpContext ctx = context(req);
            assertDoesNotThrow(() -> {
                ctx.release();
                ctx.release();
            }, "未解析表单时 decoder 为 null，release 恒为 no-op");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void releaseDelegatesToContentAfterFormParsing() {
        FullHttpRequest req = Requests.postForm("/form", "name=alice");
        try {
            FullHttpContext ctx = context(req);
            ctx.httpContent.getFormParams();   // 同包可见 protected 字段：让 decoder 落地
            assertDoesNotThrow(ctx::release);
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

    public static class QueryBean {
        private String name;
        private int age;
        private List<String> tag;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public List<String> getTag() {
            return tag;
        }

        public void setTag(List<String> tag) {
            this.tag = tag;
        }
    }

}
