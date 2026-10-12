package top.turboweb.http.middleware.router.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.middleware.router.info.RouterDefinition;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultRouterContainer} 的注册-查找契约，外加 {@link RouterContainer.TrieMatchResult}
 * 的值语义（record 的构造器与 equals/hashCode/toString 计入冻结分母，只能靠直接构造触达）。
 *
 * <p>路径规整与 trie 内部匹配分别由 {@link PathHelperTest} 与阶段 1 的
 * {@code RestUrlTrieMatchTest} 覆盖，本类只断言容器把两者接起来之后的对外行为。
 */
class DefaultRouterContainerTest {

    private static RouterDefinition definition() {
        return ctx -> "ok";
    }

    private static DefaultRouterContainer withExact(String method, String path, RouterDefinition def) {
        DefaultRouterContainer c = new DefaultRouterContainer();
        c.getExactRouterInfo().addRouter(method, path, def);
        return c;
    }

    private static DefaultRouterContainer withTrie(String method, String path, RouterDefinition def) {
        DefaultRouterContainer c = new DefaultRouterContainer();
        c.getTrieRouterInfo().addRouter(method, path, def);
        return c;
    }

    @Test
    void exactMatchFindsRegisteredPath() {
        RouterDefinition def = definition();
        assertSame(def, withExact("GET", "/a", def).exactMatch("GET", "/a"));
    }

    @Test
    void exactMatchStripsQueryStringBeforeLookup() {
        RouterDefinition def = definition();
        assertSame(def, withExact("GET", "/a", def).exactMatch("GET", "/a?x=1&y=2"));
    }

    @Test
    void exactMatchStripsExactlyOneTrailingSlash() {
        RouterDefinition def = definition();
        DefaultRouterContainer c = withExact("GET", "/a", def);
        assertSame(def, c.exactMatch("GET", "/a/"));
        assertNull(c.exactMatch("GET", "/a//"), "只裁一个尾斜杠：'/a//' 不会塌成 '/a'");
    }

    @Test
    void exactMatchKeepsRootSlash() {
        RouterDefinition def = definition();
        assertSame(def, withExact("GET", "/", def).exactMatch("GET", "/"));
    }

    @Test
    void exactMatchMissReturnsNull() {
        DefaultRouterContainer c = withExact("GET", "/a", definition());
        assertNull(c.exactMatch("GET", "/missing"));
    }

    @Test
    void exactMatchUnsupportedMethodThrowsRouterNotMatch() {
        DefaultRouterContainer c = new DefaultRouterContainer();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class, () -> c.exactMatch("HEAD", "/a"));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void trieMatchReturnsDefinitionAndPathParams() {
        RouterDefinition def = definition();
        RouterContainer.TrieMatchResult result = withTrie("GET", "/u/{id}", def).trieMatch("GET", "/u/42");
        assertSame(def, result.definition());
        assertEquals(Map.of("id", "42"), result.params());
    }

    @Test
    void trieMatchStripsQueryStringBeforeMatching() {
        RouterDefinition def = definition();
        DefaultRouterContainer c = withTrie("GET", "/u/{id}", def);
        assertSame(def, c.trieMatch("GET", "/u/42?x=1").definition());
        assertEquals(Map.of("id", "42"), c.trieMatch("GET", "/u/42?x=1").params(), "查询串不得混进参数值");
    }

    @Test
    void trieMatchMissReturnsNull() {
        DefaultRouterContainer c = withTrie("GET", "/u/{id}", definition());
        assertNull(c.trieMatch("GET", "/other"));
    }

    @Test
    void trieMatchUnsupportedMethodThrowsRouterNotMatch() {
        DefaultRouterContainer c = new DefaultRouterContainer();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class, () -> c.trieMatch("HEAD", "/a"));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void trieMatchDecodesPercentEscapesInPathParams() {
        DefaultRouterContainer c = withTrie("GET", "/u/{id}", definition());
        // 用 ASCII 转义（%20），结果不依赖 GlobalConfig.getRequestCharset() 的当前取值
        assertEquals("a b", c.trieMatch("GET", "/u/a%20b").params().get("id"));
    }

    @Test
    @DisplayName("钉住 HS-15：路径参数用 URLDecoder 解码，'+' 被当成空格")
    void plusInPathParamBecomesSpace() {
        DefaultRouterContainer c = withTrie("GET", "/u/{id}", definition());
        // URL 路径里的 '+' 是字面加号，只有 application/x-www-form-urlencoded 语义才代表空格；
        // 这里走的是 URLDecoder.decode(value, charset)（:71）。若将来改成只做百分号解码，
        // 本用例应改为 assertEquals("a+b", …)。
        // 若将来修正，本用例应改为期望正确行为
        assertEquals("a b", c.trieMatch("GET", "/u/a+b").params().get("id"));
    }

    @Test
    void controllerInstancesIsTheLiveRegistrySharedWithRegistrars() {
        // AnnoRouterManager.initRouterContainer:240 直接往这个 map 里写控制器实例，
        // 因此它必须是同一份可变引用，不能是副本。
        DefaultRouterContainer c = new DefaultRouterContainer();
        Object controller = new Object();
        c.getControllerInstances().put(String.class, controller);
        assertSame(controller, c.getControllerInstances().get(String.class));
    }

    @Test
    void trieMatchResultCarriesDefinitionAndParams() {
        RouterDefinition def = definition();
        Map<String, String> params = Map.of("id", "7");
        RouterContainer.TrieMatchResult result = new RouterContainer.TrieMatchResult(def, params);
        assertSame(def, result.definition());
        assertEquals(params, result.params());
    }

    @Test
    void trieMatchResultHasValueSemantics() {
        RouterDefinition def = definition();
        HashMap<String, String> params = new HashMap<>();
        params.put("id", "7");
        RouterContainer.TrieMatchResult a = new RouterContainer.TrieMatchResult(def, params);
        RouterContainer.TrieMatchResult equalContent = new RouterContainer.TrieMatchResult(def, Map.of("id", "7"));
        RouterContainer.TrieMatchResult otherParams = new RouterContainer.TrieMatchResult(def, Map.of("id", "8"));
        RouterContainer.TrieMatchResult otherDefinition =
                new RouterContainer.TrieMatchResult(ctx -> "other", Map.of("id", "7"));

        assertEquals(a, equalContent, "组件相等即记录相等，与 map 实现无关");
        assertEquals(a.hashCode(), equalContent.hashCode());
        assertNotEquals(a, otherParams);
        assertNotEquals(a, otherDefinition, "definition 按引用相等，另一个 lambda 实例不相等");
        assertTrue(a.toString().startsWith("TrieMatchResult["), a.toString());
        assertTrue(a.toString().contains("params={id=7}"), a.toString());
    }
}
