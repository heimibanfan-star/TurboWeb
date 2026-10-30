package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 响应头合并：Set-Cookie 累加、CORS 与 Content-* 不覆盖、其余覆盖。
 */
class HttpResponseHelperTest {

    private static HttpResponse response() {
        return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    @Test
    void absentHeadersAreCopiedOver() {
        HttpResponse source = response();
        source.headers().set("X-A", "1");
        HttpResponse target = response();
        HttpResponseHelper.mergeHeaders(source, target);
        assertEquals("1", target.headers().get("X-A"));
    }

    @Test
    void ordinaryHeaderIsOverwrittenWhenAlreadyPresent() {
        HttpResponse source = response();
        source.headers().set("X-A", "new");
        HttpResponse target = response();
        target.headers().set("X-A", "old");
        HttpResponseHelper.mergeHeaders(source, target);
        assertEquals("new", target.headers().get("X-A"),
                "非白名单头走 fall-through 的 set，语义是覆盖而非保留");
    }

    @Test
    void corsAndContentHeadersAreKept() {
        HttpResponse source = response();
        source.headers().set("Access-Control-Allow-Origin", "http://evil");
        source.headers().set("Access-Control-Allow-Methods", "DELETE");
        source.headers().set("Access-Control-Allow-Headers", "X-Evil");
        source.headers().set("Access-Control-Allow-Credentials", "true");
        source.headers().set("Content-Type", "text/plain");
        source.headers().set("Content-Length", "999");
        HttpResponse target = response();
        target.headers().set("Access-Control-Allow-Origin", "http://ok");
        target.headers().set("Access-Control-Allow-Methods", "GET");
        target.headers().set("Access-Control-Allow-Headers", "X-Ok");
        target.headers().set("Access-Control-Allow-Credentials", "false");
        target.headers().set("Content-Type", "application/json");
        target.headers().set("Content-Length", "12");
        HttpResponseHelper.mergeHeaders(source, target);
        assertEquals("http://ok", target.headers().get("Access-Control-Allow-Origin"));
        assertEquals("GET", target.headers().get("Access-Control-Allow-Methods"));
        assertEquals("X-Ok", target.headers().get("Access-Control-Allow-Headers"));
        assertEquals("false", target.headers().get("Access-Control-Allow-Credentials"));
        assertEquals("application/json", target.headers().get("Content-Type"));
        assertEquals("12", target.headers().get("Content-Length"));
    }

    @Test
    @DisplayName("F18 回归：Set-Cookie 合并保留目标既有值并追加源值")
    void setCookieMergeAppendsKeepingTargetValues() {
        HttpResponse source = response();
        source.headers().set("Set-Cookie", "a=1");
        HttpResponse target = response();
        target.headers().set("Set-Cookie", "b=2");
        HttpResponseHelper.mergeHeaders(source, target);
        assertEquals(List.of("b=2", "a=1"), target.headers().getAll("Set-Cookie"),
                "Set-Cookie 允许多值：目标原值必须保留，源值追加在后");
    }

    @Test
    void sourceWithoutTheHeaderLeavesTargetUntouched() {
        HttpResponse source = response();
        HttpResponse target = response();
        target.headers().set("X-Keep", "v");
        HttpResponseHelper.mergeHeaders(source, target);
        assertEquals("v", target.headers().get("X-Keep"));
        assertNull(target.headers().get("X-Missing"));
    }
}
