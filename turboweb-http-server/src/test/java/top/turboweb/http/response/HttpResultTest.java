package top.turboweb.http.response;

import org.junit.jupiter.api.Test;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.StatusLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpResult} 的值对象契约：状态行、头部与原始业务数据的承载，
 * 工厂方法的状态码矩阵，状态行取自框架注册表的共享口径。
 */
class HttpResultTest {

    @Test
    void carriesConstructorInjectedStatusHeadersAndData() {
        Headers headers = new Headers();
        headers.set("X-Trace-Id", "t-1");

        HttpResult result = new HttpResult(201, headers, "created");

        assertEquals(201, result.statusLine().code());
        assertSame(headers, result.headers(), "头部集合按入参原样持有，读写直作用于结果对象");
        assertEquals("t-1", result.headers().get("X-Trace-Id"));
        assertEquals("created", result.data());
    }

    @Test
    void missingHeadersDefaultsToOwnEmptyEntity() {
        HttpResult result = new HttpResult(200, "ok");

        assertTrue(result.headers().isEmpty(), "未传入头部时自建空头部实体");
    }

    @Test
    void dataMayBeNull() {
        assertNullData(new HttpResult(200));
        assertNullData(HttpResult.ok());
        assertNullData(HttpResult.err());
    }

    private static void assertNullData(HttpResult result) {
        assertEquals(null, result.data(), "未携带数据的工厂产物 data 为 null");
    }

    @Test
    void factoryMethodsCarryExpectedStatusCodes() {
        assertEquals(200, HttpResult.ok().statusLine().code());
        assertEquals(200, HttpResult.ok("body").statusLine().code());
        assertEquals(500, HttpResult.err().statusLine().code());
        assertEquals(500, HttpResult.err("boom").statusLine().code());
        assertEquals(201, HttpResult.create(201, "created").statusLine().code());
        assertEquals(404, new HttpResult(404).statusLine().code());
        assertEquals(202, new HttpResult(202, "accepted").statusLine().code());
    }

    @Test
    void createWithHeadersKeepsGivenHeaders() {
        Headers headers = new Headers();
        headers.set("X-Trace-Id", "t-1");

        HttpResult result = HttpResult.create(201, headers, "created");

        assertSame(headers, result.headers());
        assertEquals(201, result.statusLine().code());
    }

    @Test
    void registeredStatusCodeReturnsRegistrySharedStatusLine() {
        assertSame(StatusLine.OK, HttpResult.ok().statusLine(), "已收录状态码取注册表共享实例");
    }

    @Test
    void unregisteredStatusCodeStillBuildsResult() {
        assertEquals(599, new HttpResult(599).statusLine().code(), "未收录状态码不拒收，按给定值建状态行");
    }

    @Test
    void negativeStatusRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HttpResult(-1));
    }
}
