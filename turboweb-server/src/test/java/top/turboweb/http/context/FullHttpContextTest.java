package top.turboweb.http.context;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.exception.ParameterValidationException;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.context.content.HttpContent;
import top.turboweb.http.core.cookie.DefaultHttpCookieManager;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.DefaultHttpSession;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.testkit.Requests;

import java.net.SocketAddress;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                new DefaultHttpCookieManager(request.headers()), CONNECT_SESSION, SERIALIZER);
    }

    // ---------- 构造器注入与基础 getter 面（CoreHttpContext） ----------

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
    void cookieManagerReadsTheCookieHeaderOfTheRequest() {
        FullHttpRequest req = Requests.getWithCookie("/x", "sid=abc; theme=dark");
        try {
            FullHttpContext ctx = context(req);
            assertEquals("abc", ctx.cookie().getCookie("sid"));
            assertEquals("dark", ctx.cookie().getCookie("theme"));
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
                    "非 HEAD 走 new HttpContent(request)：Content-Type 已解析");
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
            // 产品码（SseResponse.setSseHeaders）写的是 "text/event-stream;charset=<GlobalConfig 响应编码>"，
            // 按 Review Focus 第 4 条：先读 GlobalConfig 当前值再定期望，不放宽为前缀匹配。
            assertEquals("text/event-stream;charset=" + GlobalConfig.getResponseCharset().name(),
                    response.headers().get(HttpHeaderNames.CONTENT_TYPE));
            SseEmitter defaultEmitter = ctx.createSseEmitter();
            assertEquals("text/event-stream", defaultEmitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
            SseEmitter sizedEmitter = ctx.createSseEmitter(8);
            assertEquals("text/event-stream", sizedEmitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 路径参数（ParamBinder） ----------

    @Test
    void paramBeforeInjectionReturnsNull() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            assertNull(ctx.param("id"));
            assertNull(ctx.paramInt("id"), "无路径参数时类型转换方法同样返回 null");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void injectedPathParamIsReadBackAndMissingNameIsNull() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ctx.injectPathParam(Map.of("id", "7"));
            assertEquals("7", ctx.param("id"));
            assertNull(ctx.param("nope"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void pathParamTypedConversions() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ctx.injectPathParam(Map.of("i", "42", "l", "99", "b", "TRUE", "d", "1.5", "date", "2026-10-06"));
            assertEquals(42, ctx.paramInt("i"));
            assertEquals(99L, ctx.paramLong("l"));
            assertTrue(ctx.paramBool("b"), "Boolean.parseBoolean 大小写不敏感");
            assertEquals(1.5, ctx.paramDouble("d"));
            assertEquals(LocalDate.of(2026, 10, 6), ctx.paramDate("date"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void pathParamIntParseFailureIsWrapped() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ctx.injectPathParam(Map.of("i", "abc"));
            assertThrows(RequestParseException.class, () -> ctx.paramInt("i"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    @DisplayName("钉住 HS-6：paramDouble/paramDate 的解析失败不包装，原样抛 unchecked")
    void pathParamDoubleAndDateFailuresAreNotWrapped() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ctx.injectPathParam(Map.of("d", "abc", "date", "2026-13-01"));
            // 与 paramInt/paramLong 的 RequestParseException 不对称。
            // 钉住 HS-6：若将来修正，本用例应改为期望正确行为（修镜像时本用例应改为断言包装类型）。
            assertThrows(NumberFormatException.class, () -> ctx.paramDouble("d"));
            assertThrows(DateTimeParseException.class, () -> ctx.paramDate("date"));
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 查询参数 ----------

    @Test
    void queriesReturnAllValuesInOrder() {
        FullHttpRequest req = Requests.get("/search?tag=a&tag=b");
        try {
            assertEquals(List.of("a", "b"), context(req).queries("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryReturnsFirstValueAndMissingIsNull() {
        FullHttpRequest req = Requests.get("/search?name=alice&name=bob");
        try {
            FullHttpContext ctx = context(req);
            assertEquals("alice", ctx.query("name"));
            assertNull(ctx.query("nope"));
            assertEquals(List.of(), ctx.queries("nope"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryDefaultIsUsedOnlyWhenAbsent() {
        FullHttpRequest req = Requests.get("/search?name=alice");
        try {
            FullHttpContext ctx = context(req);
            assertEquals("alice", ctx.query("name", "def"));
            assertEquals("def", ctx.query("nope", "def"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryValuesArePercentDecoded() {
        FullHttpRequest req = Requests.get("/search?q=a%20b");
        try {
            assertEquals("a b", context(req).query("q"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryTypedConversionsAndDefaults() {
        FullHttpRequest req = Requests.get("/search?i=42&l=99&b=true&d=1.5");
        try {
            FullHttpContext ctx = context(req);
            assertEquals(42, ctx.queryInt("i"));
            assertEquals(99L, ctx.queryLong("l"));
            assertTrue(ctx.queryBool("b"));
            assertEquals(1.5, ctx.queryDouble("d"));
            assertEquals(7L, ctx.queryLong("nope", 7L));
            assertEquals(7, ctx.queryInt("nope", 7));
            assertTrue(ctx.queryBool("nope", Boolean.TRUE));
            assertEquals(7.5, ctx.queryDouble("nope", 7.5));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void multiValuedQueryConvertsToTypedLists() {
        FullHttpRequest req = Requests.get("/search?i=1&i=2&l=3&l=4&b=true&b=false&d=1.5&d=2.5");
        try {
            FullHttpContext ctx = context(req);
            assertEquals(List.of(1, 2), ctx.queriesInt("i"));
            assertEquals(List.of(3L, 4L), ctx.queriesLong("l"));
            assertEquals(List.of(true, false), ctx.queriesBool("b"));
            assertEquals(List.of(1.5, 2.5), ctx.queriesDouble("d"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryIntParseFailureThrowsRawNumberFormat() {
        FullHttpRequest req = Requests.get("/search?i=abc");
        try {
            FullHttpContext ctx = context(req);
            // 查询侧流式转换不包装异常，与 paramInt 的 RequestParseException 不同
            assertThrows(NumberFormatException.class, () -> ctx.queryInt("i"));
        } finally {
            Requests.release(req);
        }
    }

    // ---------- loadQuery / loadForm / loadJson 绑定 ----------

    @Test
    void loadQueryMapsSingleAndMultiValuedParameters() {
        FullHttpRequest req = Requests.get("/search?name=alice&age=30&tag=a&tag=b");
        try {
            QueryBean bean = context(req).loadQuery(QueryBean.class);
            assertEquals("alice", bean.getName());
            assertEquals(30, bean.getAge());
            assertEquals(List.of("a", "b"), bean.getTag(), "多值参数以 List 交给 Jackson");
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

    // ---------- validate ----------

    @Test
    void validatePassesWhenConstraintsHold() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            assertDoesNotThrow(() -> ctx.validate(new ValidBean("alice")));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void validateThrowsWithMessagesWhenConstraintViolated() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            ParameterValidationException ex =
                    assertThrows(ParameterValidationException.class, () -> ctx.validate(new ValidBean()));
            assertEquals(List.of("name 不能为空"), ex.getErrorMsg());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadValidQueryChecksDefaultGroupAndExplicitGroupSeparately() {
        FullHttpRequest req = Requests.get("/x");
        try {
            FullHttpContext ctx = context(req);
            // 默认组不含 groups=OnCreate 的约束
            assertDoesNotThrow(() -> ctx.loadValidQuery(GroupBean.class));
            ParameterValidationException ex = assertThrows(ParameterValidationException.class,
                    () -> ctx.loadValidQuery(GroupBean.class, OnCreate.class));
            assertEquals(List.of("name 不能为空"), ex.getErrorMsg());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void loadValidFormAndJsonThrowOnViolation() {
        FullHttpRequest form = Requests.postForm("/form", "other=x");
        FullHttpRequest json = Requests.postJson("/json", "{}");
        try {
            FullHttpContext formCtx = context(form);
            FullHttpContext jsonCtx = context(json);
            assertThrows(ParameterValidationException.class, () -> formCtx.loadValidForm(ValidBean.class));
            assertThrows(ParameterValidationException.class, () -> jsonCtx.loadValidJson(ValidBean.class));
        } finally {
            Requests.release(form);
            Requests.release(json);
        }
    }

    // ---------- 编码（Review Focus 第 4 条：先读 GlobalConfig） ----------

    @Test
    void jsonCharsetComesFromTheContentTypeHeader() {
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/json",
                "application/json; charset=GBK", "{\"name\":\"张三\"}", java.nio.charset.Charset.forName("GBK"));
        try {
            assertEquals("{\"name\":\"张三\"}", new HttpContent(req).getJsonContent());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void jsonCharsetWithoutHeaderFallsBackToGlobalConfig() {
        // 先读当前值，再据其编码请求体；用例不改写 GlobalConfig（隔离八条第 6 条）
        java.nio.charset.Charset charset = GlobalConfig.getRequestCharset();
        FullHttpRequest req = Requests.raw(io.netty.handler.codec.http.HttpMethod.POST, "/json",
                "application/json", "{\"name\":\"café\"}", charset);
        try {
            assertEquals("{\"name\":\"café\"}", new HttpContent(req).getJsonContent());
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

    public static class ValidBean {
        @NotBlank(message = "name 不能为空")
        private String name;

        public ValidBean() {
        }

        public ValidBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public interface OnCreate {
    }

    public static class GroupBean {
        @NotNull(message = "name 不能为空", groups = OnCreate.class)
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
