package top.turboweb.http.processor;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.respmeta.DefaultResponseMeta;
import top.turboweb.http.context.respmeta.ResponseMeta;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.processor.convertor.DefaultHttpResponseConverter;
import top.turboweb.http.session.BackHoleSessionManager;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link MiddlewareInvokeProcessor} 的锁分支、finally 必达与 {@code setRespMeta} 两处 null 分支。
 *
 * <p>spec §5 要求"`context.release()` 与 `httpSession().expireAt()` 在 finally 中必达
 * （{@code MiddlewareInvokeProcessor.java:144-149}）"。`FullHttpContext` 在 `executeMiddleware`
 * 内部 `new` 出来，`release()` 没有可注入的观察点；但 `release()` 之后紧邻的
 * `context.httpSession().expireAt()` 会委派到注入的 {@link SessionManager}，因此用一个记录型
 * {@link SessionManager} 观察 `expireAt()` 被调用，即可证明整段 finally 执行——成功路径与
 * "中间件抛异常"路径各一条。若把 finally 里的语句移出、或删掉 finally，异常路径的用例会红。
 * `release()` 本身的可观测性局限见"移交阶段 3/4"。全部纯内存，无通道、无 sleep、无线程。
 */
class MiddlewareInvokeProcessorTest {

    /** 记录型会话管理器：记录 expireAt 的 sessionId，exist 只认预先声明的 id。 */
    private static final class RecordingSessionManager implements SessionManager {

        private final Set<String> existing;
        private final List<String> expired = new ArrayList<>();

        RecordingSessionManager(String... existingIds) {
            this.existing = new HashSet<>(Arrays.asList(existingIds));
        }

        @Override
        public void setAttr(String sessionId, String key, Object value) {
        }

        @Override
        public void setAttr(String sessionId, String key, Object value, long timeout) {
        }

        @Override
        public Object getAttr(String sessionId, String key) {
            return null;
        }

        @Override
        public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
            return null;
        }

        @Override
        public void remAttr(String sessionId, String key) {
        }

        @Override
        public boolean exist(String sessionId) {
            return existing.contains(sessionId);
        }

        @Override
        public boolean createSessionMap(String sessionId) {
            return false;
        }

        @Override
        public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
        }

        @Override
        public String sessionManagerName() {
            return "recording";
        }

        @Override
        public void expireAt(String sessionId) {
            expired.add(sessionId);
        }
    }

    private static MiddlewareInvokeProcessor processor(Middleware chain, SessionManager manager) {
        SessionManagerHolder holder = () -> manager;
        return new MiddlewareInvokeProcessor(
                chain, holder, new DefaultHttpResponseConverter(new JacksonJsonSerializer()),
                new JacksonJsonSerializer());
    }

    private static Middleware returning(final Object payload) {
        return new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                return payload;
            }
        };
    }

    private static Middleware throwing(final RuntimeException error) {
        return new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                throw error;
            }
        };
    }

    private static Middleware attaching(final ResponseMeta meta, final Object payload) {
        return new Middleware() {
            @Override
            public Object invoke(HttpContext ctx) {
                ctx.attachment().attach(ResponseMeta.ATTACH_KEY, meta);
                return payload;
            }
        };
    }

    private static FullHttpRequest requestWithSession(String sessionId) {
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/x");
        if (sessionId != null) {
            request.headers().set(HttpHeaderNames.COOKIE, "JSESSIONID=" + sessionId);
        }
        return request;
    }

    @Test
    @DisplayName("BackHole 会话管理器：走无锁分支，结果仍被转换")
    void backHoleSessionManagerSkipsLockAndStillConverts() {
        MiddlewareInvokeProcessor processor =
                processor(returning("ok"), new BackHoleSessionManager());

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(requestWithSession(null), null);

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("ok", response.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("存在会话且成功返回：finally 中 expireAt(会话id) 必达")
    void existingSessionIsRenewedInFinallyOnSuccess() {
        RecordingSessionManager manager = new RecordingSessionManager("sess-1");
        MiddlewareInvokeProcessor processor = processor(returning("ok"), manager);

        processor.invoke(requestWithSession("sess-1"), null);

        assertEquals(List.of("sess-1"), manager.expired,
                "成功路径下 finally 中的 expireAt 必须被调用");
    }

    @Test
    @DisplayName("中间件抛异常：异常继续外抛，finally 中 expireAt 仍必达")
    void throwingMiddlewareStillRenewsSessionInFinally() {
        RecordingSessionManager manager = new RecordingSessionManager("sess-1");
        MiddlewareInvokeProcessor processor = processor(throwing(new IllegalStateException("boom")), manager);

        assertThrows(IllegalStateException.class,
                () -> processor.invoke(requestWithSession("sess-1"), null));

        assertEquals(List.of("sess-1"), manager.expired,
                "异常路径下 finally 中的 expireAt 仍必须被调用");
    }

    @Test
    @DisplayName("setRespMeta：挂载的 status 与 contentType 都非空时两者都生效")
    void responseMetaWithBothFieldsAppliesStatusAndContentType() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        meta.status(201);
        meta.contentType(ContentType.create("text/plain", StandardCharsets.UTF_8));
        MiddlewareInvokeProcessor processor = processor(attaching(meta, "body"), new BackHoleSessionManager());

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(requestWithSession(null), null);

        assertEquals(201, response.status().code());
        assertEquals("text/plain; charset=UTF-8",
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("setRespMeta：status 为 null 时保留转换器状态码，contentType 无 charset 时补默认 charset")
    void responseMetaWithNullStatusKeepsConvertedStatusAndFallsBackToDefaultCharset() {
        DefaultResponseMeta meta = new DefaultResponseMeta(); // status 保持 null
        meta.contentType(ContentType.create("application/xml")); // charset 保持 null
        MiddlewareInvokeProcessor processor = processor(attaching(meta, "body"), new BackHoleSessionManager());

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(requestWithSession(null), null);

        assertEquals(HttpResponseStatus.OK, response.status(), "status 为 null 时不覆盖转换器状态码");
        assertEquals("application/xml; charset=" + Charset.defaultCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }

    @Test
    @DisplayName("setRespMeta：contentType 为 null 时保留转换器内容类型，只应用状态码")
    void responseMetaWithNullContentTypeKeepsConvertedContentType() {
        DefaultResponseMeta meta = new DefaultResponseMeta();
        meta.status(204); // contentType 保持 null
        MiddlewareInvokeProcessor processor = processor(attaching(meta, "body"), new BackHoleSessionManager());

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(requestWithSession(null), null);

        assertEquals(204, response.status().code());
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE),
                "contentType 为 null 时不覆盖转换器写下的内容类型");
    }

    @Test
    @DisplayName("未挂载 ResponseMeta：提前 return，状态码与内容类型都取转换器结果")
    void noResponseMetaLeavesConvertedResponseUntouched() {
        MiddlewareInvokeProcessor processor = processor(returning("body"), new BackHoleSessionManager());

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(requestWithSession(null), null);

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals("text/html;charset=" + GlobalConfig.getResponseCharset().name(),
                response.headers().get(HttpHeaderNames.CONTENT_TYPE));
    }
}
