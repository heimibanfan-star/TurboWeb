package top.turboweb.http.middleware.router.info;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboMethodNotSupportException;
import top.turboweb.commons.exception.TurboRouterException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> info.addRouter(null, "/a", definition()));
        assertEquals("不支持的方法:", ex.getMessage());
    }

    @Test
    void unsupportedMethodIsRejectedOnAdd() {
        TrieRouterInfo info = new TrieRouterInfo();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> info.addRouter("HEAD", "/a", definition()));
        assertEquals("不支持的方法:HEAD", ex.getMessage());
    }

    @Test
    void duplicateStaticPathRaisesRouterRepeat() {
        TrieRouterInfo info = new TrieRouterInfo();
        info.addRouter("GET", "/a", definition());
        TurboRouterException ex = assertThrows(TurboRouterException.class,
                () -> info.addRouter("GET", "/a", definition()));
        assertEquals(TurboRouterException.ROUTER_REPEAT, ex.getCode());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause(),
                "重复是底层 trie 的 insert 抛 IAE 后再由容器包装成 ROUTER_REPEAT");
    }

    @Test
    @DisplayName("钉住 HS-12：参数路径重复注册不报 ROUTER_REPEAT，后注册者静默胜出")
    void duplicateParameterPathIsSilentlyOverwritten() {
        TrieRouterInfo info = new TrieRouterInfo();
        RouterDefinition first = definition();
        RouterDefinition second = definition();
        info.addRouter("GET", "/u/{id}", first);
        // 期望：与静态路径一致地抛 ROUTER_REPEAT。
        // 实际：commons AbstractTrie:187 用未归一化的 "{id}" 查重复，而节点以 "{id:str}" 入 map，
        // 检测落空 ⇒ 不抛，并把 map 里的值换成新定义。修 commons F1 后本用例应改为 assertThrows。
        // 若将来修正，本用例应改为期望正确行为
        assertDoesNotThrow(() -> info.addRouter("GET", "/u/{id}", second));
        assertEquals(second, info.getPathTrie("GET").match("/u/9").value, "第二次注册替换了首个定义");
    }

    @Test
    void getPathTrieWithUnsupportedMethodRaisesRouterNotMatch() {
        TrieRouterInfo info = new TrieRouterInfo();
        TurboRouterException ex = assertThrows(TurboRouterException.class, () -> info.getPathTrie("HEAD"));
        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, ex.getCode());
        assertEquals("不支持的方法:HEAD", ex.getMessage());
    }

    @Test
    @DisplayName("钉住 HS-13：getPathTrie(null) 抛 NPE，而 addRouter(null,…) 有守卫")
    void getPathTrieWithNullMethodThrowsNpe() {
        TrieRouterInfo info = new TrieRouterInfo();
        // addRouter 用 Optional 兜底后抛 RuntimeException("不支持的方法:")（:34）；
        // getPathTrie 少了这层守卫，直接 method.toUpperCase() 撞 NPE（:55）。修时可改为同样的兜底。
        // 若将来修正，本用例应改为期望正确行为
        assertThrows(NullPointerException.class, () -> info.getPathTrie(null));
    }

    @Test
    @DisplayName("钉住 HS-14：不支持的方法在两个容器抛不同异常类型")
    void unsupportedMethodUsesDifferentExceptionTypesAcrossContainers() {
        // 钉住现状：同一"不支持的方法"语义在两个容器分别抛 TurboMethodNotSupportException、
        // TurboRouterException 与裸 RuntimeException，调用方无法用统一类型兜住。
        // 若将来修正，本用例应改为期望正确行为
        assertInstanceOf(TurboMethodNotSupportException.class, assertThrows(RuntimeException.class,
                () -> new ExactRouterInfo().getRouterDefinition("HEAD")));
        assertInstanceOf(TurboRouterException.class, assertThrows(RuntimeException.class,
                () -> new TrieRouterInfo().getPathTrie("HEAD")));
        RuntimeException fromAdd = assertThrows(RuntimeException.class,
                () -> new TrieRouterInfo().addRouter("HEAD", "/a", definition()));
        assertFalse(fromAdd instanceof TurboRouterException, "addRouter 的不支持方法异常比 getPathTrie 更弱");
        assertFalse(fromAdd instanceof TurboMethodNotSupportException);
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
        TurboRouterException ex = assertThrows(TurboRouterException.class,
                () -> info.addRouter("GET", "/a", definition()));
        assertEquals(TurboRouterException.ROUTER_REPEAT, ex.getCode());
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
        TurboMethodNotSupportException fromAdd = assertThrows(TurboMethodNotSupportException.class,
                () -> info.addRouter("HEAD", "/a", definition()));
        assertEquals("不支持的方法:HEAD", fromAdd.getMessage());
        assertThrows(TurboMethodNotSupportException.class, () -> info.getRouterDefinition("HEAD"));
    }

    @Test
    void nullMethodIsTreatedAsUnsupported() {
        ExactRouterInfo info = new ExactRouterInfo();
        TurboMethodNotSupportException ex = assertThrows(TurboMethodNotSupportException.class,
                () -> info.getRouterDefinition(null));
        assertEquals("不支持的方法:", ex.getMessage());
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
