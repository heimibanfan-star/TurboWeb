package top.turboweb.http.server;

import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.server.config.ServerConfig;
import top.turboweb.server.runtime.ServerRuntime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BootStrapWebServer} 的未启动面：构造、应用注册顺序、h2/ssl fail-fast、未启动关闭。
 *
 * <p>全部在不创建线程组、通道与端口的前提下完成：构造器只持有配置；h2 校验位于任何资源创建
 * 之前（{@code BootStrapWebServer.java:224-227}），故反射直调私有 {@code startup()} 取证而不是
 * {@code start(0)}；关闭走 {@code serverChannel == null} 与 {@code shutdown()} 的空操作分支。
 * 真实启停属 L3，见本任务末尾"移交阶段 3/4"。
 */
class BootStrapWebServerTest {

    /** 只记录关闭的运行时替身，用于观察 server.close() 是否按注册顺序触达运行时管理器。 */
    private static final class CloseRecordingRuntime implements ServerRuntime {

        private final String name;
        private final List<String> closed;

        CloseRecordingRuntime(String name, List<String> closed) {
            this.name = name;
            this.closed = closed;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
        }

        @Override
        public void close() {
            closed.add(name);
        }
    }

    @Test
    @DisplayName("构造器拒绝 null 配置")
    void constructorRejectsNullConfig() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new BootStrapWebServer((ServerConfig) null));

        assertEquals("The ServerConfig must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("未启动就 close：不抛异常，仍经应用管理器按注册顺序释放应用")
    void closeWithoutStartReleasesRegisteredRuntimes() {
        List<String> closed = new ArrayList<>();
        BootStrapWebServer server = new BootStrapWebServer();   // 默认配置，覆盖无参构造器
        server.registerRuntimeLast(new CloseRecordingRuntime("app", closed));

        assertDoesNotThrow(server::close);

        assertEquals(List.of("app"), closed, "未启动时只跳过通道关闭，应用资源仍须释放");
    }

    @Test
    @DisplayName("registerRuntimeFirst/Last 的顺序在关闭时按注册序复现")
    void registrationOrderIsPreservedAtClose() {
        List<String> closed = new ArrayList<>();
        BootStrapWebServer server = new BootStrapWebServer(ServerConfig.builder().build());
        server.registerRuntimeLast(new CloseRecordingRuntime("A", closed));
        server.registerRuntimeFirst(new CloseRecordingRuntime("B", closed));
        server.registerRuntimeLast(new CloseRecordingRuntime("C", closed));

        server.close();

        assertEquals(List.of("B", "A", "C"), closed);
    }

    @Test
    @DisplayName("重复 close 不抛异常")
    void closeIsRepeatable() {
        List<String> closed = new ArrayList<>();
        BootStrapWebServer server = new BootStrapWebServer(ServerConfig.builder().build());
        server.registerRuntimeLast(new CloseRecordingRuntime("app", closed));

        assertDoesNotThrow(server::close);
        assertDoesNotThrow(server::close);

        assertTrue(closed.contains("app"), "重复关闭后应用仍应处于已关闭状态");
    }

    @Test
    @DisplayName("未启动就 shutdown：空操作，不抛异常")
    void shutdownBeforeStartIsNoOp() {
        BootStrapWebServer server = new BootStrapWebServer(ServerConfig.builder().build());

        assertDoesNotThrow(server::shutdown);
        assertDoesNotThrow(server::shutdown);
    }

    @Test
    @DisplayName("enableHttp2 且未配置 ssl：startup 在任何资源创建前 fail-fast")
    void http2WithoutSslFailsFastBeforeCreatingResources() throws Exception {
        BootStrapWebServer server = new BootStrapWebServer(
                ServerConfig.builder().enableHttp2(true).build());
        Method startup = BootStrapWebServer.class.getDeclaredMethod("startup");
        startup.setAccessible(true);

        Throwable thrown = null;
        try {
            startup.invoke(server);
        } catch (InvocationTargetException e) {
            thrown = e.getCause();
        } finally {
            // 校验发生在 :224-227，正常路径下从未创建资源，close 是空操作；
            // 若校验失效（回归/变异），startup 已建线程组与通道，这里负责回收，避免用例泄漏。
            server.close();
        }

        assertNotNull(thrown, "enableHttp2 且未配置 ssl 时必须 fail-fast");
        assertEquals(IllegalArgumentException.class, thrown.getClass());
        assertEquals("SSL is required for HTTP/2", thrown.getMessage());
    }
}
