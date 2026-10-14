package top.turboweb.http.server;

import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.utils.resource.ScopedResource;
import top.turboweb.http.server.application.Application;
import top.turboweb.http.server.config.ServerConfig;

import java.lang.reflect.Constructor;
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
 * {@link TurboWebHttpServer} 的未启动面：构造、应用注册顺序、h2/ssl fail-fast、未启动关闭。
 *
 * <p>全部在不创建线程组、通道与端口的前提下完成：构造器只持有配置；h2 校验位于任何资源创建
 * 之前（{@code TurboWebHttpServer.java:224-227}），故反射直调私有 {@code startup()} 取证而不是
 * {@code start(0)}；关闭走 {@code serverChannel == null} 与 {@code shutdown()} 的空操作分支。
 * {@code $ZeroCopyThreadFactory} 只构造 {@link Thread} 对象、绝不 {@code start}，用来钉住命名与
 * 守护属性。真实启停属 L3，见本任务末尾"移交阶段 3/4"。
 */
class TurboWebHttpServerTest {

    /** 只记录关闭的应用替身，用于观察 server.close() 是否按注册顺序触达应用管理器。 */
    private static final class CloseRecordingApplication implements Application {

        private final String name;
        private final List<String> closed;

        CloseRecordingApplication(String name, List<String> closed) {
            this.name = name;
            this.closed = closed;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
        }

        @Override
        public ScopedResource getScopedResource() {
            return new ScopedResource();
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
                () -> new TurboWebHttpServer((ServerConfig) null));

        assertEquals("The ServerConfig must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("未启动就 close：不抛异常，仍经应用管理器按注册顺序释放应用")
    void closeWithoutStartReleasesRegisteredApplications() {
        List<String> closed = new ArrayList<>();
        TurboWebHttpServer server = new TurboWebHttpServer();   // 默认配置，覆盖无参构造器
        server.registerApplicationLast(new CloseRecordingApplication("app", closed));

        assertDoesNotThrow(server::close);

        assertEquals(List.of("app"), closed, "未启动时只跳过通道关闭，应用资源仍须释放");
    }

    @Test
    @DisplayName("registerApplicationFirst/Last 的顺序在关闭时按注册序复现")
    void registrationOrderIsPreservedAtClose() {
        List<String> closed = new ArrayList<>();
        TurboWebHttpServer server = new TurboWebHttpServer(ServerConfig.builder().build());
        server.registerApplicationLast(new CloseRecordingApplication("A", closed));
        server.registerApplicationFirst(new CloseRecordingApplication("B", closed));
        server.registerApplicationLast(new CloseRecordingApplication("C", closed));

        server.close();

        assertEquals(List.of("B", "A", "C"), closed);
    }

    @Test
    @DisplayName("重复 close 不抛异常")
    void closeIsRepeatable() {
        List<String> closed = new ArrayList<>();
        TurboWebHttpServer server = new TurboWebHttpServer(ServerConfig.builder().build());
        server.registerApplicationLast(new CloseRecordingApplication("app", closed));

        assertDoesNotThrow(server::close);
        assertDoesNotThrow(server::close);

        assertTrue(closed.contains("app"), "重复关闭后应用仍应处于已关闭状态");
    }

    @Test
    @DisplayName("未启动就 shutdown：空操作，不抛异常")
    void shutdownBeforeStartIsNoOp() {
        TurboWebHttpServer server = new TurboWebHttpServer(ServerConfig.builder().build());

        assertDoesNotThrow(server::shutdown);
        assertDoesNotThrow(server::shutdown);
    }

    @Test
    @DisplayName("enableHttp2 且未配置 ssl：startup 在任何资源创建前 fail-fast")
    void http2WithoutSslFailsFastBeforeCreatingResources() throws Exception {
        TurboWebHttpServer server = new TurboWebHttpServer(
                ServerConfig.builder().enableHttp2(true).build());
        Method startup = TurboWebHttpServer.class.getDeclaredMethod("startup");
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

    @Test
    @DisplayName("$ZeroCopyThreadFactory 给线程命名为 zero-copy-thread-N 并设为守护线程")
    void zeroCopyThreadFactoryNamesAndDaemonizesThreads() throws Exception {
        Class<?> factoryClass =
                Class.forName("top.turboweb.http.server.TurboWebHttpServer$ZeroCopyThreadFactory");
        Constructor<?> constructor = factoryClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object factory = constructor.newInstance();
        Method newThread = factoryClass.getDeclaredMethod("newThread", Runnable.class);
        newThread.setAccessible(true);

        // 只构造 Thread 对象、绝不 start：不产生任何运行中的线程
        Thread first = (Thread) newThread.invoke(factory, (Runnable) () -> { });
        Thread second = (Thread) newThread.invoke(factory, (Runnable) () -> { });

        assertEquals("zero-copy-thread-0", first.getName());
        assertEquals("zero-copy-thread-1", second.getName());
        assertTrue(first.isDaemon(), "零拷贝线程必须是守护线程");
        assertTrue(second.isDaemon(), "零拷贝线程必须是守护线程");
    }
}
