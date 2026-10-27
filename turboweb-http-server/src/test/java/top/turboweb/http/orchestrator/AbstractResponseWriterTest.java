package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.http.testkit.Stubs;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AbstractResponseWriter} 注入护栏的行为锚：线程池注入状态由原子布尔维护，
 * 同一写出器实例仅可注入一次——二次注入抛 IllegalStateException，
 * 且被拒的二次注入不得覆写首次注入的线程池。
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
    @DisplayName("二次注入抛 IllegalStateException，消息钉文案")
    void secondInjectionIsRejected() {
        RecordingWriter writer = new RecordingWriter();
        writer.injectThreadPool(Stubs.threadPool());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> writer.injectThreadPool(Stubs.directExecutorThreadPool()));
        assertEquals("The disk IO thread pool has already been injected and can not be injected again", thrown.getMessage());
    }

    @Test
    @DisplayName("被拒的二次注入不覆写首次注入的线程池")
    void rejectedInjectionKeepsFirstPool() {
        RecordingWriter writer = new RecordingWriter();
        ThreadPool first = Stubs.threadPool();
        writer.injectThreadPool(first);

        assertThrows(IllegalStateException.class, () -> writer.injectThreadPool(Stubs.directExecutorThreadPool()));

        assertSame(first, writer.injectedPool(), "重复注入被拒后必须仍持有首次注入的池");
    }
}
