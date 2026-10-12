package top.turboweb.http.middleware.router.info;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.exception.RouteHandlerException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 两类路由信息容器：{@link ExactRouterInfo}（精确 map）与 {@link TrieRouterInfo}（每方法一棵 trie）。
 *
 * <p>它们各自决定"注册重复"与"方法不支持"的表现，是容器语义的直接证据源；
 * trie 内部的匹配算法由阶段 1 覆盖，本类只断言容器层。
 */
class RouterInfoTest {

    private static final String[] METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH"};

    /** 每个定义一个独立实例：命中判定靠身份，不看行为。 */
    private static RouterDefinition definition() {
        return ctx -> "ok";
    }

    @Test
    void addRouterCoversEverySupportedMethod() {
        TrieRouterInfo info = new TrieRouterInfo();
        for (String method : METHODS) {
            RouterDefinition def = definition();
            info.addRouter(method, "/r", def);
            assertEquals(def, info.getPathTrie(method).match("/r").value, method);
        }
    }

    @Test
    void methodLookupIsCaseInsensitive() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouterDefinition def = definition();
        info.addRouter("get", "/a", def);
        assertEquals(def, info.getPathTrie("GET").match("/a").value);
        assertEquals(def, info.getPathTrie("get").match("/a").value);
    }

    @Test
    void triesAreIsolatedPerMethod() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouterDefinition get = definition();
        RouterDefinition post = definition();
        info.addRouter("GET", "/a", get);
        info.addRouter("POST", "/a", post);
        assertEquals(get, info.getPathTrie("GET").match("/a").value);
        assertEquals(post, info.getPathTrie("POST").match("/a").value);
    }

    @Test
    void nullMethodIsRejectedOnAdd() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.addRouter(null, "/a", definition()));
        assertEquals("不支持的方法:", ex.getMessage());
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void unsupportedMethodIsRejectedOnAdd() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.addRouter("HEAD", "/a", definition()));
        assertEquals("不支持的方法:HEAD", ex.getMessage());
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void duplicateStaticPathRaisesRouterRepeat() {
        TrieRouterInfo info = new TrieRouterInfo();
        info.addRouter("GET", "/a", definition());
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.addRouter("GET", "/a", definition()));
        assertEquals(RouteHandlerException.ROUTER_REPEAT, ex.getCode());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause(),
                "重复是底层 trie 的 insert 抛 IAE 后再由容器包装成 ROUTER_REPEAT");
    }

    @Test
    @DisplayName("HS-12（已修，随 commons F1）：参数路径重复注册与静态路径一致地报 ROUTER_REPEAT")
    void duplicateParameterPathRaisesRouterRepeat() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouterDefinition first = definition();
        info.addRouter("GET", "/u/{id}", first);
        // commons F1 修复后 trie 判重改用归一化键并抛 IAE，容器照静态路径的同一通道包成 ROUTER_REPEAT。
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.addRouter("GET", "/u/{id}", definition()));
        assertEquals(RouteHandlerException.ROUTER_REPEAT, ex.getCode());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertSame(first, info.getPathTrie("GET").match("/u/9").value, "被拒绝的注册不得顶掉原定义");
    }

    @Test
    void getPathTrieWithUnsupportedMethodRaisesRouterNotMatch() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class, () -> info.getPathTrie("HEAD"));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
        assertEquals("不支持的方法:HEAD", ex.getMessage());
    }

    @Test
    @DisplayName("HS-13 回归：getPathTrie(null) 归一空串后按不支持方法抛 ROUTER_NOT_MATCH")
    void getPathTrieWithNullMethodRaisesRouterNotMatch() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class, () -> info.getPathTrie(null));
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
        assertEquals("不支持的方法:", ex.getMessage(), "空值与 addRouter 的守卫同款归一后入消息");
    }

    @Test
    @DisplayName("HS-14 回归：不支持的方法在两个容器四个抛出点统一抛 RouteHandlerException(ROUTER_NOT_MATCH)")
    void unsupportedMethodUsesUnifiedExceptionAcrossContainers() {
        assertInstanceOf(RouteHandlerException.class, assertThrows(RuntimeException.class,
                () -> new ExactRouterInfo().getRouterDefinition("HEAD")));
        assertInstanceOf(RouteHandlerException.class, assertThrows(RuntimeException.class,
                () -> new ExactRouterInfo().addRouter("HEAD", "/a", definition())));
        assertInstanceOf(RouteHandlerException.class, assertThrows(RuntimeException.class,
                () -> new TrieRouterInfo().getPathTrie("HEAD")));
        assertInstanceOf(RouteHandlerException.class, assertThrows(RuntimeException.class,
                () -> new TrieRouterInfo().addRouter("HEAD", "/a", definition())));
    }

    @Test
    void exactAddRouterCoversEverySupportedMethod() {
        ExactRouterInfo info = new ExactRouterInfo();
        for (String method : METHODS) {
            RouterDefinition def = definition();
            info.addRouter(method, "/r", def);
            assertSame(def, info.getRouterDefinition(method).get("/r"));
        }
    }

    @Test
    void exactMethodLookupIsCaseInsensitive() {
        ExactRouterInfo info = new ExactRouterInfo();
        RouterDefinition def = definition();
        info.addRouter("get", "/a", def);
        assertSame(def, info.getRouterDefinition("GET").get("/a"));
        assertSame(def, info.getRouterDefinition("get").get("/a"));
    }

    @Test
    void exactDuplicatePathRaisesRouterRepeatWithMessage() {
        ExactRouterInfo info = new ExactRouterInfo();
        info.addRouter("get", "/a", definition());
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.addRouter("GET", "/a", definition()));
        assertEquals(RouteHandlerException.ROUTER_REPEAT, ex.getCode());
        assertEquals("路由重复: method:GET, path:/a", ex.getMessage(), "方法名先大写化再入消息");
    }

    @Test
    void samePathDifferentMethodsDoNotConflict() {
        ExactRouterInfo info = new ExactRouterInfo();
        RouterDefinition get = definition();
        RouterDefinition delete = definition();
        info.addRouter("GET", "/a", get);
        info.addRouter("DELETE", "/a", delete);
        assertSame(get, info.getRouterDefinition("GET").get("/a"));
        assertSame(delete, info.getRouterDefinition("DELETE").get("/a"));
    }

    @Test
    void unsupportedMethodIsRejectedForAddAndGet() {
        ExactRouterInfo info = new ExactRouterInfo();
        RouteHandlerException fromAdd = assertThrows(RouteHandlerException.class,
                () -> info.addRouter("HEAD", "/a", definition()));
        assertEquals("不支持的方法:HEAD", fromAdd.getMessage());
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, fromAdd.getCode());
        assertThrows(RouteHandlerException.class, () -> info.getRouterDefinition("HEAD"));
    }

    @Test
    void nullMethodIsTreatedAsUnsupported() {
        ExactRouterInfo info = new ExactRouterInfo();
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> info.getRouterDefinition(null));
        assertEquals("不支持的方法:", ex.getMessage());
        assertEquals(RouteHandlerException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void getRouterDefinitionExposesTheLiveMap() {
        // getRouterDefinition 直接返回内部 map（:32/:36），调用方可绕过 addRouter 的重复检测写入。
        // 现存生产调用方（DefaultRouterContainer.exactMatch:37）只读；这里钉住"返回的是同一实例"。
        ExactRouterInfo info = new ExactRouterInfo();
        RouterDefinition def = definition();
        Map<String, RouterDefinition> live = info.getRouterDefinition("GET");
        live.put("/bypass", def);
        assertSame(def, info.getRouterDefinition("GET").get("/bypass"));
    }
}
