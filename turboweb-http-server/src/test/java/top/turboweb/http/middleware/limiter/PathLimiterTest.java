package top.turboweb.http.middleware.limiter;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.http.testkit.Stubs;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PathLimiter} 的放行 / 拒绝 / {@code doReject} 三路（spec §5）。
 * <p>
 * 时间窗用注入的极端 interval（3600 秒）把“有没有令牌”变成确定量：用例内不可能补币，不 sleep、不对齐时钟。
 */
class PathLimiterTest {

    private static HttpContext ctx(String uri) {
        return Stubs.httpContext(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
    }

    @Test
    void noMatchingRulePassesThrough() {
        List<String> events = new ArrayList<>();
        PathLimiter limiter = new PathLimiter();
        limiter.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", limiter.invoke(ctx("/other?x=1")));
        assertEquals(List.of("invoke:down"), events);
    }

    @Test
    void firstRequestWithinQuotaPassesThrough() {
        List<String> events = new ArrayList<>();
        PathLimiter limiter = new PathLimiter();
        limiter.addRule("/api", 1, 3600); // 1 小时窗口：用例内不可能补币
        limiter.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", limiter.invoke(ctx("/api?x=1")));
        assertEquals(List.of("invoke:down"), events);
    }

    @Test
    void exhaustedQuotaRejectsWith429AndSkipsDownstream() {
        List<String> events = new ArrayList<>();
        PathLimiter limiter = new PathLimiter();
        limiter.addRule("/api", 1, 3600);
        limiter.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", limiter.invoke(ctx("/api?x=1"))); // 第 1 个令牌
        Object rejected = limiter.invoke(ctx("/api?x=1"));   // 令牌耗尽

        assertInstanceOf(HttpResponse.class, rejected);
        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, ((HttpResponse) rejected).status());
        assertEquals(List.of("invoke:down"), events, "拒绝时下游必须没被调用过");
    }

    @Test
    void doRejectBuilds429WithPlainBody() {
        HttpResponse rejected = new PathLimiter().doReject(ctx("/x?y=1"));

        assertEquals(HttpResponseStatus.TOO_MANY_REQUESTS, rejected.status());
        assertEquals("too many request",
                ((FullHttpResponse) rejected).content().toString(StandardCharsets.UTF_8));
        assertEquals("16", rejected.headers().get("Content-Length"));
    }

    @Test
    void trailingSlashRuleIsNormalized() {
        PathLimiter limiter = new PathLimiter();
        limiter.addRule("/api/", 1, 3600);
        limiter.setNext(new RecordingMiddleware("down", new ArrayList<>(), "ok"));

        // 规则以 /api/ 注册，请求以 /api 命中：证明 addRule 去掉了尾斜杠
        assertEquals("ok", limiter.invoke(ctx("/api?x=1")));
        assertInstanceOf(HttpResponse.class, limiter.invoke(ctx("/api?x=1")));
    }

    @Test
    void addRuleRejectsInvalidArguments() {
        PathLimiter limiter = new PathLimiter();
        assertThrows(IllegalArgumentException.class, () -> limiter.addRule(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.addRule("", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.addRule("/a/{id}", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.addRule("/a", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> limiter.addRule("/a", 1, 0));
    }

    @Test
    @DisplayName("钉住 HS-22：无查询串的 URI 让 PathLimiter 抛 StringIndexOutOfBoundsException")
    void uriWithoutQueryStringThrows() {
        PathLimiter limiter = new PathLimiter();
        limiter.setNext(new RecordingMiddleware("down", new ArrayList<>(), "ok"));

        // PathLimiter.java:24 无 contains("?") 守卫，path.indexOf("?") == -1 时 substring(0, -1) 直接抛
        // 钉住 HS-22：若将来修正，本用例应改为期望正确行为（加守卫后 /api 应正常放行到下游）。
        assertThrows(StringIndexOutOfBoundsException.class, () -> limiter.invoke(ctx("/api")));
    }
}
