package top.turboweb.http.core.cookie;

import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DefaultHttpCookies} 的请求头解析与 {@code Set-Cookie} 取值产出（spec §5：cookie 行）。
 *
 * <p>夹具只用 Netty 请求头对象（无通道）：解析侧以 {@link DefaultHttpHeaders} 承载 Cookie 头，
 * 产出侧直接取 {@link DefaultHttpCookies#toSetCookies(java.nio.charset.Charset)} 的返回列表，不构造响应对象。
 * 请求头默认启用 netty 校验（与真实解码路径一致）；只有"以 0x20 开头的头值"这一形态
 * 需要 {@code new DefaultHttpHeaders(false)}——真实 decoder 产出的报文会经过该校验。
 */
class DefaultHttpCookiesTest {

    @Test
    @DisplayName("请求头无 Cookie 时得到空管理器")
    void absentCookieHeaderYieldsEmptyManager() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());

        assertNull(cookies.getCookie("a"));
        assertTrue(cookies.getCookies().isEmpty());
        assertTrue(cookies.toSetCookies(StandardCharsets.UTF_8).isEmpty(), "无任何状态时不得产出 Set-Cookie 取值");
    }

    @Test
    @DisplayName("单个 Cookie 被解析")
    void singleCookieIsParsed() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(cookieHeader("a=1"));

        assertEquals("1", cookies.getCookie("a"));
        assertEquals(Map.of("a", "1"), cookies.getCookies());
    }

    @Test
    @DisplayName("多个 Cookie 按分号拆分")
    void multipleCookiesAreSplitOnSemicolon() {
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("a=1; b=2; c=3"));

        assertEquals(Map.of("a", "1", "b", "2", "c", "3"), cookies.getCookies());
    }

    @Test
    @DisplayName("值里可以含等号，按第一个等号切分")
    void valueMayContainEqualsSign() {
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("token=abc=def"));

        assertEquals("abc=def", cookies.getCookie("token"));
    }

    @Test
    @DisplayName("键与值两侧空白被裁剪")
    void surroundingWhitespaceIsTrimmed() {
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("a = 1; b = 2"));

        assertEquals(Map.of("a", "1", "b", "2"), cookies.getCookies());
    }

    @Test
    @DisplayName("无等号、或等号在首的段被静默跳过")
    void malformedSegmentsAreSkipped() {
        // 段 "garbage" 无等号（indexOf 返回 -1）、段 "=orphan" 等号在首（indexOf 返回 0），
        // 两类都被 index > 0 守卫跳过；空白键段（如 " =orphan"）由键 trim 后判空跳过，
        // 见 blankKeySegmentIsSkipped。要命中"等号在首"分支，'=' 必须真的是段首字符。
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("a=1;garbage;=orphan;b=2"));

        assertEquals(Map.of("a", "1", "b", "2"), cookies.getCookies(),
                "没有 '=' 的段与 '=' 开头的段都不进映射，且不抛异常");
    }

    @Test
    @DisplayName("空值合法，不会被当成缺失")
    void emptyValueIsKept() {
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("a=; b=1"));

        assertEquals("", cookies.getCookie("a"));
        assertEquals("1", cookies.getCookie("b"));
        assertNull(cookies.getCookie("zz"), "未命中的键返回 null");
    }

    @Test
    @DisplayName("同名 Cookie 后者覆盖前者")
    void laterDuplicateOverwritesEarlier() {
        DefaultHttpCookies cookies =
                new DefaultHttpCookies(cookieHeader("a=1; a=2"));

        assertEquals("2", cookies.getCookie("a"));
        assertEquals(Map.of("a", "2"), cookies.getCookies());
    }

    @Test
    @DisplayName("HS-7 回归：空键段（空白+等号）与'等号在首'一样被静默跳过，不再让构造期抛 IAE")
    void blankKeySegmentIsSkipped() {
        // 修复前钉住的现状：'=' 前有空白、等号不在段首的段（" =oops"）通过 index>0 守卫后，
        // 键 trim 成空串、DefaultHttpCookie.checkKey 抛 "key can not be empty"，同头其它 Cookie 全部丢失。
        // 修复后按登记注翻正：空键段与 '='/无 '=' 的畸形段同样被跳过，其余键值照常收录。
        DefaultHttpCookies cookies = assertDoesNotThrow(
                () -> new DefaultHttpCookies(cookieHeader("a=1; =oops; b=2")));

        assertEquals(Map.of("a", "1", "b", "2"), cookies.getCookies(),
                "空键段被跳过，同头的合法段不受牵连");
        assertNull(cookies.getCookie(""), "空键不得进入映射");
    }

    @Test
    @DisplayName("键以空白开头时（非校验头夹具）空白被裁掉")
    void leadingWhitespaceKeyIsTrimmed() {
        // 该头值以 0x20 开头，netty 默认校验会拒收（HttpHeaderValidationUtil 视首字符 < 0x21 为非法），
        // 只能换非校验构造器；真实 decoder 路径产出的形态是内部空白，不需要这条夹具
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(HttpHeaderNames.COOKIE, "  a=1");
        DefaultHttpCookies cookies = new DefaultHttpCookies(headers);

        assertEquals("1", cookies.getCookie("a"));
    }

    @Test
    @DisplayName("setCookie(key,value) 可读回并按序列化写出")
    void setCookieByKeyValueIsRetrievableAndWritten() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie("a", "1");

        assertEquals("1", cookies.getCookie("a"));
        assertEquals(Map.of("a", "1"), cookies.getCookies());
        assertEquals(List.of("a=1; Path=/"), cookies.toSetCookies(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("setCookie 的 Consumer 重载把属性写进 Set-Cookie")
    void consumerOverloadAppliesAttributes() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie("t", "v", c -> {
            c.setPath("/app");
            c.setMaxAge(60);
            c.setHttpOnly(true);
        });

        assertEquals(List.of("t=v; Path=/app; Max-Age=60; HttpOnly"), cookies.toSetCookies(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("setCookie(HttpCookie) 同名覆盖，写出只有一条")
    void setCookieInstanceOverwritesSameName() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie(new DefaultHttpCookie("a", "1"));
        cookies.setCookie(new DefaultHttpCookie("a", "2"));

        assertEquals("2", cookies.getCookie("a"));
        assertEquals(List.of("a=2; Path=/"), cookies.toSetCookies(StandardCharsets.UTF_8),
                "toWriteHttpCookies 是按名字的集合，同名只产出一条头取值");
    }

    @Test
    @DisplayName("getCookies 返回副本，改动不影响管理器")
    void getCookiesReturnsCopy() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(cookieHeader("a=1"));
        Map<String, String> snapshot = cookies.getCookies();
        snapshot.put("b", "2");

        assertEquals(1, cookies.getCookies().size(), "视图被改动不得反写回内部映射");
        assertNull(cookies.getCookie("b"));
    }

    @Test
    @DisplayName("删除请求携带的 Cookie：产出 Max-Age=0 的过期取值")
    void removingRequestCookieExpiresItInResponse() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(cookieHeader("a=1; b=2"));
        cookies.removeCookie("a");

        assertNull(cookies.getCookie("a"));
        assertEquals("2", cookies.getCookie("b"));
        assertEquals(List.of("a=; Path=/; Max-Age=0"), cookies.toSetCookies(StandardCharsets.UTF_8), "只过期 a，b 不受影响");
    }

    @Test
    @DisplayName("删除本次新写入的 Cookie：直接取消，不写过期头")
    void removingNewlyWrittenCookieCancelsIt() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie("a", "1");
        cookies.removeCookie("a");

        assertNull(cookies.getCookie("a"));
        assertTrue(cookies.toSetCookies(StandardCharsets.UTF_8).isEmpty(), "既没写新值，也没写过期的空值");
    }

    @Test
    @DisplayName("删除不存在的 Cookie 是空操作")
    void removingUnknownCookieIsNoOp() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.removeCookie("nope");

        assertTrue(cookies.toSetCookies(StandardCharsets.UTF_8).isEmpty());
        assertTrue(cookies.getCookies().isEmpty());
    }

    @Test
    @DisplayName("clearToWriteCookies 只撤销待写入，不动已读到的值")
    void clearToWriteCookiesSuppressesPendingWrites() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie("a", "1");
        cookies.clearToWriteCookies();

        assertEquals("1", cookies.getCookie("a"), "清的是待写队列，不是 Cookie 映射");
        assertTrue(cookies.toSetCookies(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    @DisplayName("clearAll 丢弃新写入并把请求原有 Cookie 全部标记过期")
    void clearAllDropsNewAndExpiresExisting() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(cookieHeader("old=1"));
        cookies.setCookie("new", "2");
        cookies.clearAll();

        // clearAll 只把"本次新写入"的移出映射并撤销；请求原有的键留在映射里、
        // 仅被快照进待删集合——写出前仍可读，直到取值随响应发出才由浏览器删除。
        assertEquals("1", cookies.getCookie("old"), "存量 Cookie 只被标记过期，不被 clearAll 提前移出映射");
        assertNull(cookies.getCookie("new"));
        assertEquals(List.of("old=; Path=/; Max-Age=0"), cookies.toSetCookies(StandardCharsets.UTF_8),
                "新写入的被直接丢弃，原有的一条写出过期头");
    }

    @Test
    @DisplayName("待写与待删同时存在时都写：新值在前，过期头在后")
    void writesAndRemovalsAreBothEmitted() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(cookieHeader("old=1"));
        cookies.setCookie("new", "2");
        cookies.removeCookie("old");

        assertEquals(List.of("new=2; Path=/", "old=; Path=/; Max-Age=0"), cookies.toSetCookies(StandardCharsets.UTF_8),
                "两个循环各只有一个元素，顺序确定：先写待写入，再写过期");
    }

    @Test
    @DisplayName("取值不自动清空队列，重复调用产出同一批")
    void toSetCookiesDoesNotClearQueue() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie("a", "1");

        assertEquals(List.of("a=1; Path=/"), cookies.toSetCookies(StandardCharsets.UTF_8));
        assertEquals(List.of("a=1; Path=/"), cookies.toSetCookies(StandardCharsets.UTF_8),
                "javadoc 明示调用后不自动清空，需显式 clearToWriteCookies()");
    }

    @Test
    @DisplayName("取值时复用 HttpCookie 的 URL 编码")
    void writtenValueGoesThroughCookieEncoding() {
        DefaultHttpCookies cookies = new DefaultHttpCookies(new DefaultHttpHeaders());
        cookies.setCookie(new DefaultHttpCookie("n", "a b"));

        assertEquals(List.of("n=a+b; Path=/"), cookies.toSetCookies(StandardCharsets.UTF_8), "空格编码为 '+'");
    }

    /** 夹具：构造一个带 Cookie 头的请求头集合（启用 netty 校验，与真实解码路径一致）。 */
    private static HttpHeaders cookieHeader(String rawValue) {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.COOKIE, rawValue);
        return headers;
    }
}
