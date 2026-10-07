package top.turboweb.http.cookie;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultHttpCookieManager} 的请求头解析与 {@code Set-Cookie} 写出（spec §5：cookie 行）。
 *
 * <p>夹具全是纯 Netty 对象（无通道），响应用 {@link DefaultHttpResponse}。
 * 请求头默认启用 netty 校验（与真实解码路径一致）；只有"以 0x20 开头的头值"这一形态
 * 需要 {@code new DefaultHttpHeaders(false)}——真实 decoder 产出的报文会经过该校验。
 */
class DefaultHttpCookieManagerTest {

    @Test
    @DisplayName("请求头无 Cookie 时得到空管理器")
    void absentCookieHeaderYieldsEmptyManager() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());

        assertNull(manager.getCookie("a"));
        assertTrue(manager.getCookies().isEmpty());

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertTrue(setCookies(response).isEmpty(), "无任何状态时不得写出 Set-Cookie");
    }

    @Test
    @DisplayName("单个 Cookie 被解析")
    void singleCookieIsParsed() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(cookieHeader("a=1"));

        assertEquals("1", manager.getCookie("a"));
        assertEquals(Map.of("a", "1"), manager.getCookies());
    }

    @Test
    @DisplayName("多个 Cookie 按分号拆分")
    void multipleCookiesAreSplitOnSemicolon() {
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("a=1; b=2; c=3"));

        assertEquals(Map.of("a", "1", "b", "2", "c", "3"), manager.getCookies());
    }

    @Test
    @DisplayName("值里可以含等号，按第一个等号切分")
    void valueMayContainEqualsSign() {
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("token=abc=def"));

        assertEquals("abc=def", manager.getCookie("token"));
    }

    @Test
    @DisplayName("键与值两侧空白被裁剪")
    void surroundingWhitespaceIsTrimmed() {
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("a = 1; b = 2"));

        assertEquals(Map.of("a", "1", "b", "2"), manager.getCookies());
    }

    @Test
    @DisplayName("无等号、或等号在首的段被静默跳过")
    void malformedSegmentsAreSkipped() {
        // 分号后不留空格："; =orphan" 会让段变成 " =orphan"，indexOf('=')==1 通过 index>0 守卫、
        // 键 trim 后为空串 → 构造期抛 IAE（那正是 blankKeySegmentThrowsDuringConstruction 钉住的形态）。
        // 要命中"等号在首被跳过"的 index==0 分支，'=' 必须真的是段首字符。
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("a=1;garbage;=orphan;b=2"));

        assertEquals(Map.of("a", "1", "b", "2"), manager.getCookies(),
                "没有 '=' 的段与 '=' 开头的段都不进映射，且不抛异常");
    }

    @Test
    @DisplayName("空值合法，不会被当成缺失")
    void emptyValueIsKept() {
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("a=; b=1"));

        assertEquals("", manager.getCookie("a"));
        assertEquals("1", manager.getCookie("b"));
        assertNull(manager.getCookie("zz"), "未命中的键返回 null");
    }

    @Test
    @DisplayName("同名 Cookie 后者覆盖前者")
    void laterDuplicateOverwritesEarlier() {
        DefaultHttpCookieManager manager =
                new DefaultHttpCookieManager(cookieHeader("a=1; a=2"));

        assertEquals("2", manager.getCookie("a"));
        assertEquals(Map.of("a", "2"), manager.getCookies());
    }

    @Test
    @DisplayName("钉住 HS-7：空键段（空白+等号）让构造期抛 IAE，而不是像其它畸形段那样被跳过")
    void blankKeySegmentThrowsDuringConstruction() {
        // "a=1; =oops" 的第二段是 " =oops"：indexOf('=') == 1 > 0 通过了守卫，
        // 但 substring(0,1).trim() 得到空串，HttpCookie 的 checkKey 抛 "key can not be empty"。
        // 对比 malformedSegmentsAreSkipped：'=' 在首的段会被跳过，空白键却把整个构造打挂。
        // 钉住 HS-7：若将来修正，本用例应改为期望正确行为（若将来把守卫收紧为对键做非空校验后 continue，本用例应改为断言该段被跳过）。
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new DefaultHttpCookieManager(cookieHeader("a=1; =oops")));
        assertEquals("key can not be empty", ex.getMessage());
    }

    @Test
    @DisplayName("键以空白开头时（非校验头夹具）空白被裁掉")
    void leadingWhitespaceKeyIsTrimmed() {
        // 该头值以 0x20 开头，netty 默认校验会拒收（HttpHeaderValidationUtil 视首字符 < 0x21 为非法），
        // 只能换非校验构造器；真实 decoder 路径产出的形态是内部空白，不需要这条夹具
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(HttpHeaderNames.COOKIE, "  a=1");
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(headers);

        assertEquals("1", manager.getCookie("a"));
    }

    @Test
    @DisplayName("setCookie(key,value) 可读回并按序列化写出")
    void setCookieByKeyValueIsRetrievableAndWritten() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie("a", "1");

        assertEquals("1", manager.getCookie("a"));
        assertEquals(Map.of("a", "1"), manager.getCookies());

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("a=1; Path=/"), setCookies(response));
    }

    @Test
    @DisplayName("setCookie 的 Consumer 重载把属性写进 Set-Cookie")
    void consumerOverloadAppliesAttributes() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie("t", "v", c -> {
            c.setPath("/app");
            c.setMaxAge(60);
            c.setHttpOnly(true);
        });

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("t=v; Path=/app; Max-Age=60; HttpOnly"), setCookies(response));
    }

    @Test
    @DisplayName("setCookie(HttpCookie) 同名覆盖，写出只有一条")
    void setCookieInstanceOverwritesSameName() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie(new HttpCookie("a", "1"));
        manager.setCookie(new HttpCookie("a", "2"));

        assertEquals("2", manager.getCookie("a"));

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("a=2; Path=/"), setCookies(response),
                "toWriteHttpCookies 是按名字的集合，同名只产出一条头");
    }

    @Test
    @DisplayName("getCookies 返回副本，改动不影响管理器")
    void getCookiesReturnsCopy() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(cookieHeader("a=1"));
        Map<String, String> snapshot = manager.getCookies();
        snapshot.put("b", "2");

        assertEquals(1, manager.getCookies().size(), "视图被改动不得反写回内部映射");
        assertNull(manager.getCookie("b"));
    }

    @Test
    @DisplayName("删除请求携带的 Cookie：写 Max-Age=0 的过期头")
    void removingRequestCookieExpiresItInResponse() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(cookieHeader("a=1; b=2"));
        manager.removeCookie("a");

        assertNull(manager.getCookie("a"));
        assertEquals("2", manager.getCookie("b"));

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("a=; Path=/; Max-Age=0"), setCookies(response), "只过期 a，b 不受影响");
    }

    @Test
    @DisplayName("删除本次新写入的 Cookie：直接取消，不写过期头")
    void removingNewlyWrittenCookieCancelsIt() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie("a", "1");
        manager.removeCookie("a");

        assertNull(manager.getCookie("a"));

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertTrue(setCookies(response).isEmpty(), "既没写新值，也没写过期的空值");
    }

    @Test
    @DisplayName("删除不存在的 Cookie 是空操作")
    void removingUnknownCookieIsNoOp() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.removeCookie("nope");

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertTrue(setCookies(response).isEmpty());
        assertTrue(manager.getCookies().isEmpty());
    }

    @Test
    @DisplayName("clearToWriteCookies 只撤销待写入，不动已读到的值")
    void clearToWriteCookiesSuppressesPendingWrites() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie("a", "1");
        manager.clearToWriteCookies();

        assertEquals("1", manager.getCookie("a"), "清的是待写队列，不是 Cookie 映射");

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertTrue(setCookies(response).isEmpty());
    }

    @Test
    @DisplayName("clearAll 丢弃新写入并把请求原有 Cookie 全部标记过期")
    void clearAllDropsNewAndExpiresExisting() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(cookieHeader("old=1"));
        manager.setCookie("new", "2");
        manager.clearAll();

        // clearAll 只把"本次新写入"的移出映射并撤销（:186-189）；请求原有的键留在映射里、
        // 仅被快照进待删集合（:190）——写出前仍可读，直到响应头发出才由浏览器删除。
        assertEquals("1", manager.getCookie("old"), "存量 Cookie 只被标记过期，不被 clearAll 提前移出映射");
        assertNull(manager.getCookie("new"));

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("old=; Path=/; Max-Age=0"), setCookies(response),
                "新写入的被直接丢弃，原有的一条写出过期头");
    }

    @Test
    @DisplayName("待写与待删同时存在时都写：新值在前，过期头在后")
    void writesAndRemovalsAreBothEmitted() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(cookieHeader("old=1"));
        manager.setCookie("new", "2");
        manager.removeCookie("old");

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("new=2; Path=/", "old=; Path=/; Max-Age=0"), setCookies(response),
                "两个循环各只有一个元素，顺序确定：先写待写入，再写过期");
    }

    @Test
    @DisplayName("写出不自动清空队列，重复调用会重复追加")
    void setCookieForResponseDoesNotClearQueue() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie("a", "1");

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        manager.setCookieForResponse(response);
        assertEquals(List.of("a=1; Path=/", "a=1; Path=/"), setCookies(response),
                "javadoc 明示调用后不自动清空，需显式 clearToWriteCookies()");
    }

    @Test
    @DisplayName("写出时复用 HttpCookie 的 URL 编码")
    void writtenValueGoesThroughCookieEncoding() {
        DefaultHttpCookieManager manager = new DefaultHttpCookieManager(new DefaultHttpHeaders());
        manager.setCookie(new HttpCookie("n", "a b"));

        HttpResponse response = response();
        manager.setCookieForResponse(response);
        assertEquals(List.of("n=a+b; Path=/"), setCookies(response), "空格编码为 '+'");
    }

    /** 夹具：构造一个带 Cookie 头的请求头集合（启用 netty 校验，与真实解码路径一致）。 */
    private static HttpHeaders cookieHeader(String rawValue) {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.COOKIE, rawValue);
        return headers;
    }

    /** 夹具：一个空的响应对象，用来接住写出的 Set-Cookie。 */
    private static HttpResponse response() {
        return new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    /** 夹具：读回全部 Set-Cookie 头（保序）。 */
    private static List<String> setCookies(HttpResponse response) {
        return response.headers().getAll(HttpHeaderNames.SET_COOKIE);
    }
}
