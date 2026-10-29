package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.testkit.Stubs;
import top.turboweb.server.runtime.ResourceException;
import top.turboweb.server.runtime.ResourceManager;
import top.turboweb.server.runtime.ResourceProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AbstractResponseWriter} 初始化护栏的行为锚：初始化状态由原子布尔维护，
 * 同一写出器实例仅可初始化一次——二次初始化抛 IllegalStateException，
 * 且被拒的二次初始化不得覆写首次从资源提供者取到的磁盘IO线程池；
 * 资源提供者未登记磁盘IO线程池时初始化即失败。
 */
class AbstractResponseWriterTest {

    /** 最小被测写出器：只暴露基类注入面，doWrite 无凭据。 */
    private static final class RecordingWriter extends AbstractResponseWriter {

        @Override
        protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
            return null;
        }

        ThreadPool injectedPool() {
            return threadPool;
        }
    }

    @Test
    @DisplayName("初始化从资源提供者按名取出磁盘IO线程池")
    void initExtractsDiskPoolFromProvider() {
        ThreadPool pool = Stubs.threadPool();
        RecordingWriter writer = new RecordingWriter();

        writer.initWriter(Stubs.diskPoolProvider(pool));

        assertSame(pool, writer.injectedPool(), "初始化必须从资源提供者取出磁盘IO线程池存入基类字段");
    }

    @Test
    @DisplayName("二次初始化抛 IllegalStateException，消息钉文案")
    void secondInitIsRejected() {
        RecordingWriter writer = new RecordingWriter();
        writer.initWriter(Stubs.diskPoolProvider(Stubs.threadPool()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> writer.initWriter(Stubs.diskPoolProvider(Stubs.directExecutorThreadPool())));
        assertEquals("The response writer has already been initialized and can not be initialized again", thrown.getMessage());
    }

    @Test
    @DisplayName("被拒的二次初始化不覆写首次注入的线程池")
    void rejectedInitKeepsFirstPool() {
        RecordingWriter writer = new RecordingWriter();
        ThreadPool first = Stubs.threadPool();
        writer.initWriter(Stubs.diskPoolProvider(first));

        assertThrows(IllegalStateException.class,
                () -> writer.initWriter(Stubs.diskPoolProvider(Stubs.directExecutorThreadPool())));

        assertSame(first, writer.injectedPool(), "重复初始化被拒后必须仍持有首次注入的池");
    }

    @Test
    @DisplayName("资源提供者未登记磁盘IO线程池时初始化即失败")
    void initRejectsProviderWithoutDiskPool() {
        RecordingWriter writer = new RecordingWriter();
        ResourceProvider provider = new ResourceManager.Builder().build();

        assertThrows(ResourceException.class, () -> writer.initWriter(provider),
                "提供者缺磁盘IO线程池须在初始化时暴露，不得推迟到首次写出");
    }
}
