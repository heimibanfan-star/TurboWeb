package top.turboweb.http.middleware.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.RouterContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link VersionRouterManager} 的三条硬规则与版本分发（spec §5：middleware/router）。
 *
 * <p>版本号取自 {@code ctx.query("version")}，用内存版 ctx 就够了；子管理器用夹具桩
 * （覆写 {@code invoke} 返回哨兵、记录调用次数），因此"派发给了哪一个"是可直接断言的确定量。
 */
class VersionRouterManagerTest {

    /** 夹具：按 query 里的 version 选子管理器。 */
    private static final class TestVersionManager extends VersionRouterManager {
        @Override
        protected RouterManager getRouterManager(HttpContext context, Managers managers) {
            return managers.getRouterManager(context.query("version"));
        }
    }

    /** 夹具：子管理器桩——不碰容器，只记录被调用。 */
    private static final class StubRouterManager extends RouterManager {
        private final Object result;
        int invokeCount;
        int initCount;

        StubRouterManager(Object result) {
            this.result = result;
        }

        @Override
        protected RouterContainer getRouterContainer() {
            return null;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            invokeCount++;
            return result;
        }

        @Override
        public void init(Middleware chain) {
            initCount++;
        }
    }

    private static HttpContext ctx(String version) {
        return new FullHttpContext(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/x?version=" + version),
                null, null, null, null);
    }

    @Test
    void addRouterManagerRejectsNestedVersionManager() {
        TestVersionManager manager = new TestVersionManager();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> manager.addRouterManager("v1", new TestVersionManager()));

        assertTrue(ex.getMessage().contains("Cannot add a VersionRouterManager"),
                ex.getMessage());
    }

    @Test
    void addRouterManagerRejectsDuplicateVersion() {
        TestVersionManager manager = new TestVersionManager();
        manager.addRouterManager("v1", new StubRouterManager("V1"));

        TurboRouterException ex = assertThrows(TurboRouterException.class,
                () -> manager.addRouterManager("v1", new StubRouterManager("V1-dup")));

        assertEquals(TurboRouterException.ROUTER_REPEAT, ex.getCode());
        assertTrue(ex.getMessage().contains("v1"), ex.getMessage());
    }

    @Test
    void invokeDelegatesToTheManagerOfTheRequestedVersion() {
        TestVersionManager manager = new TestVersionManager();
        StubRouterManager v1 = new StubRouterManager("V1");
        StubRouterManager v2 = new StubRouterManager("V2");
        manager.addRouterManager("v1", v1);
        manager.addRouterManager("v2", v2);

        assertEquals("V1", manager.invoke(ctx("v1")));
        assertEquals(1, v1.invokeCount);
        assertEquals(0, v2.invokeCount, "只允许被选中的那个子管理器接到调用");
    }

    @Test
    void invokeThrowsRouterNotMatchForUnknownVersion() {
        TestVersionManager manager = new TestVersionManager();
        manager.addRouterManager("v1", new StubRouterManager("V1"));

        TurboRouterException ex = assertThrows(TurboRouterException.class,
                () -> manager.invoke(ctx("v9")));

        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, ex.getCode());
        assertTrue(ex.getMessage().contains("v9"),
                "未匹配版本的异常消息应带上请求的版本号: " + ex.getMessage());
    }

    @Test
    void invokeOnEmptyManagersThrowsRouterNotMatch() {
        TestVersionManager manager = new TestVersionManager();

        TurboRouterException ex = assertThrows(TurboRouterException.class,
                () -> manager.invoke(ctx("v1")));

        assertEquals(TurboRouterException.ROUTER_NOT_MATCH, ex.getCode());
    }

    @Test
    void getRouterContainerIsAlwaysNull() {
        assertNull(new TestVersionManager().getRouterContainer(),
                "版本管理器自身不持有容器，路由由子管理器负责");
    }

    @Test
    void initPropagatesToEverySubManagerOnce() {
        TestVersionManager manager = new TestVersionManager();
        StubRouterManager v1 = new StubRouterManager("V1");
        StubRouterManager v2 = new StubRouterManager("V2");
        manager.addRouterManager("v1", v1);
        manager.addRouterManager("v2", v2);

        manager.init(null);

        assertEquals(1, v1.initCount);
        assertEquals(1, v2.initCount, "每个已注册版本的子管理器都要被 init 一次");
    }

    @Test
    void initOnEmptyManagersIsNoop() {
        TestVersionManager manager = new TestVersionManager();

        assertDoesNotThrow(() -> manager.init(null));
    }
}
