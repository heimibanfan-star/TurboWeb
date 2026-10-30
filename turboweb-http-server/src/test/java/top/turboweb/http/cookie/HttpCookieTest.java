package top.turboweb.http.cookie;
import top.turboweb.http.cookie.HttpCookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DefaultHttpCookie} 的构造校验与 {@code Set-Cookie} 序列化（spec §5：cookie 行"过期与路径"）。
 *
 * <p>只断言既有语义。类里没有 secure/sameSite/expires 属性，也没有读时钟的过期判定，
 * 因此这里全部是纯值断言，无时序。
 */
class HttpCookieTest {

    @Test
    @DisplayName("默认属性：只写 key=value 与默认 Path=/")
    void defaultsProduceMinimalSetCookie() {
        // path 字段默认就是 "/"，不是 null——写出的头必然带 "; Path=/"
        assertEquals("sid=abc; Path=/", new DefaultHttpCookie("sid", "abc").toSetCookie(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("key 为 null 抛 NPE")
    void nullKeyIsRejected() {
        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> new DefaultHttpCookie(null, "v"));
        assertEquals("key can not be null", ex.getMessage());
    }

    @Test
    @DisplayName("key 为空串抛 IAE")
    void emptyKeyIsRejected() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> new DefaultHttpCookie("", "v"));
        assertEquals("key can not be empty", ex.getMessage());
    }

    @Test
    @DisplayName("value 为 null 抛 NPE，空串合法")
    void nullValueIsRejectedButEmptyIsAllowed() {
        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> new DefaultHttpCookie("k", null));
        assertEquals("value can not be null", ex.getMessage());
        assertEquals("k=; Path=/", new DefaultHttpCookie("k", "").toSetCookie(StandardCharsets.UTF_8), "空值要写成空串，且尾部仍是默认 Path");
    }

    @Test
    @DisplayName("value 按传入字符集做 URL 编码")
    void valueIsUrlEncoded() {
        // 空格 -> '+'，'/' -> %2F，'=' -> %3D。这些 ASCII 码点在 UTF-8 与任何 ASCII 兼容字符集下编码一致，
        // 所以本断言不依赖具体字符集取值，换传其它 ASCII 兼容字符集仍成立
        assertEquals("k=a+b%2Fc%3Dd; Path=/", new DefaultHttpCookie("k", "a b/c=d").toSetCookie(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Path 只接受非空白值，空/null 保留默认 /")
    void pathIgnoresBlankAndKeepsDefault() {
        HttpCookie set = new DefaultHttpCookie("c", "v");
        set.setPath("/app");
        assertEquals("c=v; Path=/app", set.toSetCookie(StandardCharsets.UTF_8));

        // 若 blank 被写进去，path 会变成空串，toSetCookie 将整段 Path 省略 → 断言值会变，能分辨
        HttpCookie blank = new DefaultHttpCookie("c", "v");
        blank.setPath("");
        blank.setPath(null);
        assertEquals("c=v; Path=/", blank.toSetCookie(StandardCharsets.UTF_8), "空白 path 必须保持默认 /");
    }

    @Test
    @DisplayName("Domain 只在设置非空白值时写出")
    void domainIsWrittenOnlyWhenSet() {
        HttpCookie set = new DefaultHttpCookie("c", "v");
        set.setDomain(".example.com");
        assertEquals("c=v; Path=/; Domain=.example.com", set.toSetCookie(StandardCharsets.UTF_8));

        HttpCookie blank = new DefaultHttpCookie("c", "v");
        blank.setDomain("");
        blank.setDomain(null);
        assertEquals("c=v; Path=/", blank.toSetCookie(StandardCharsets.UTF_8), "domain 为空时整段省略，不是写出空值");
    }

    @Test
    @DisplayName("Max-Age 只在 maxAge>=0 时写出，0 表示立即过期")
    void maxAgeIsWrittenOnlyWhenNonNegative() {
        assertEquals("c=v; Path=/", new DefaultHttpCookie("c", "v").toSetCookie(StandardCharsets.UTF_8),
                "默认 -1 是会话 Cookie，不写 Max-Age");

        HttpCookie zero = new DefaultHttpCookie("c", "v");
        zero.setMaxAge(0);
        assertEquals("c=v; Path=/; Max-Age=0", zero.toSetCookie(StandardCharsets.UTF_8), "0 必须写出，这是删除 Cookie 的形态");

        HttpCookie fixed = new DefaultHttpCookie("c", "v");
        fixed.setMaxAge(3600);
        assertEquals("c=v; Path=/; Max-Age=3600", fixed.toSetCookie(StandardCharsets.UTF_8));

        HttpCookie negative = new DefaultHttpCookie("c", "v");
        negative.setMaxAge(-5);
        assertEquals("c=v; Path=/", negative.toSetCookie(StandardCharsets.UTF_8), "任何负数都不写 Max-Age");
    }

    @Test
    @DisplayName("HttpOnly 只在为 true 时写出")
    void httpOnlyFlagIsWrittenWhenEnabled() {
        HttpCookie on = new DefaultHttpCookie("c", "v");
        on.setHttpOnly(true);
        assertEquals("c=v; Path=/; HttpOnly", on.toSetCookie(StandardCharsets.UTF_8));

        HttpCookie off = new DefaultHttpCookie("c", "v");
        off.setHttpOnly(false);
        assertEquals("c=v; Path=/", off.toSetCookie(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("属性写出顺序固定：Path、Domain、Max-Age、HttpOnly")
    void attributeOrderIsStable() {
        HttpCookie c = new DefaultHttpCookie("n", "v");
        c.setPath("/app");
        c.setDomain(".example.com");
        c.setMaxAge(60);
        c.setHttpOnly(true);
        assertEquals("n=v; Path=/app; Domain=.example.com; Max-Age=60; HttpOnly", c.toSetCookie(StandardCharsets.UTF_8),
                "分隔符恒为 \"; \"，属性顺序即插入顺序，浏览器侧依赖这个字节形态");
    }

    @Test
    @DisplayName("equals/hashCode 覆盖全部属性")
    void equalsAndHashCodeCoverEveryAttribute() {
        HttpCookie base = configured("c", "v", "/p", ".d", 10, true);

        assertEquals(base, configured("c", "v", "/p", ".d", 10, true));
        assertEquals(base.hashCode(), configured("c", "v", "/p", ".d", 10, true).hashCode());

        assertNotEquals(base, configured("C", "v", "/p", ".d", 10, true), "key 参与相等判定");
        assertNotEquals(base, configured("c", "V", "/p", ".d", 10, true), "value 参与相等判定");
        assertNotEquals(base, configured("c", "v", "/q", ".d", 10, true), "path 参与相等判定");
        assertNotEquals(base, configured("c", "v", "/p", ".e", 10, true), "domain 参与相等判定");
        assertNotEquals(base, configured("c", "v", "/p", ".d", 11, true), "maxAge 参与相等判定");
        assertNotEquals(base, configured("c", "v", "/p", ".d", 10, false), "httpOnly 参与相等判定");
    }

    @Test
    @DisplayName("toString 暴露全部属性，便于排查")
    void toStringExposesAllAttributes() {
        assertEquals(
                "HttpCookie{key='c', value='v', path='/', domain='null', maxAge=-1, httpOnly=false}",
                new DefaultHttpCookie("c", "v").toString());
    }

    /** 局部夹具：属性的 setter 语法噪音大，用它压掉重复样板。 */
    private static HttpCookie configured(String key, String value, String path,
                                         String domain, long maxAge, boolean httpOnly) {
        HttpCookie c = new DefaultHttpCookie(key, value);
        c.setPath(path);
        c.setDomain(domain);
        c.setMaxAge(maxAge);
        c.setHttpOnly(httpOnly);
        return c;
    }
}
