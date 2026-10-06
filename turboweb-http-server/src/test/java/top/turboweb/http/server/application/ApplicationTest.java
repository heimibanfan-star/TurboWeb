package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.utils.resource.ScopedResource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Application} 默认 {@code close()} 的契约：释放作用域、按登记顺序释放、幂等，
 * 单资源失败不阻断其余（spec §5 server/application 行）。
 *
 * <p>用一个不覆写 {@code close()} 的最小实现来覆盖接口默认方法本身；磁盘池/中间件链
 * 这些真实资源的登记与顺序放在 {@code HttpRequestApplicationTest} 里断言。
 */
class ApplicationTest {

    @Test
    void closeReleasesScopedResourcesInRegistrationOrder() {
        List<String> events = new ArrayList<>();
        TestApplication app = new TestApplication();
        app.getScopedResource().register(new RecordingCloseable("a", events));
        app.getScopedResource().register(new RecordingCloseable("b", events));

        app.close();

        assertEquals(List.of("close:a", "close:b"), events,
                "默认 close() 必须释放作用域，且顺序等于登记顺序");
    }

    @Test
    void closeIsIdempotent() {
        List<String> events = new ArrayList<>();
        TestApplication app = new TestApplication();
        app.getScopedResource().register(new RecordingCloseable("a", events));

        app.close();
        app.close();

        assertEquals(List.of("close:a"), events, "重复 close() 为空操作，资源不得被释放第二次");
    }

    @Test
    void closeContinuesAfterSingleResourceFailure() {
        List<String> events = new ArrayList<>();
        TestApplication app = new TestApplication();
        app.getScopedResource().register((AutoCloseable) () -> {
            throw new IllegalStateException("释放失败");
        });
        app.getScopedResource().register(new RecordingCloseable("b", events));

        assertDoesNotThrow(app::close, "单个资源释放异常只记录日志，不得向外抛");

        assertEquals(List.of("close:b"), events, "前一个资源失败不得阻断后一个资源的释放");
    }

    @Test
    void getScopedResourceReturnsSameInstance() {
        TestApplication app = new TestApplication();
        assertSame(app.getScopedResource(), app.getScopedResource());
    }

    /** 最小应用实现：不覆写 close()，命中的就是 {@link Application} 的默认实现。 */
    private static final class TestApplication implements Application {

        private final ScopedResource scope = new ScopedResource();

        @Override
        public void configure(ChannelPipeline pipeline) {
            throw new UnsupportedOperationException("L1 不装配 pipeline");
        }

        @Override
        public ScopedResource getScopedResource() {
            return scope;
        }
    }

    /** 记录型可关闭道具：close() 只把名字写进调用方列表。 */
    private static final class RecordingCloseable implements AutoCloseable {

        private final String name;
        private final List<String> events;

        RecordingCloseable(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void close() {
            events.add("close:" + name);
        }
    }
}
