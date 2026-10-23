package top.turboweb.http.server.manager;

import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.server.runtime.ServerRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ServerRuntimeManager} 的注册顺序、null 校验与关闭隔离（spec §5：server/manager）。
 *
 * <p>装配顺序（{@link ServerRuntimeManager#configure(ChannelPipeline)} 的调用序）与关闭顺序共用
 * 同一个注册列表，而 configure 需要一条真实的 {@link ChannelPipeline}（属 L2，需 EmbeddedChannel），
 * 因此这里以 {@code close()} 的调用序作为列表顺序的 L1 可观测面；configure 的非 null 路径由
 * {@code ServerChannelInitializerTest} 经 EmbeddedChannel 覆盖。
 */
class ServerRuntimeManagerTest {

    /** 记录型运行时替身：configure/close 各落一条事件，用于观察注册顺序。 */
    private static final class RecordingRuntime implements ServerRuntime {

        private final String name;
        private final List<String> events;

        RecordingRuntime(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
            events.add("configure:" + name);
        }

        @Override
        public void close() {
            events.add("close:" + name);
        }
    }

    /** 关闭必抛的运行时替身：先记录再抛，用来验证单个应用失败不阻断其余应用。 */
    private static final class FailingRuntime implements ServerRuntime {

        private final String name;
        private final List<String> events;

        FailingRuntime(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
        }

        @Override
        public void close() {
            events.add("close:" + name);
            throw new IllegalStateException("模拟应用关闭失败");
        }
    }

    @Test
    @DisplayName("addFirst 插到队头：先 addLast(A) 再 addFirst(B)，关闭序为 B、A")
    void addFirstInsertsBeforePreviouslyRegisteredRuntimes() {
        List<String> events = new ArrayList<>();
        ServerRuntimeManager manager = new ServerRuntimeManager();
        manager.addLast(new RecordingRuntime("A", events));
        manager.addFirst(new RecordingRuntime("B", events));

        manager.close();

        assertEquals(List.of("close:B", "close:A"), events);
    }

    @Test
    @DisplayName("addLast 排到队尾：addLast(A) 后 addLast(B)，关闭序为 A、B")
    void addLastAppendsAfterPreviouslyRegisteredRuntimes() {
        List<String> events = new ArrayList<>();
        ServerRuntimeManager manager = new ServerRuntimeManager();
        manager.addLast(new RecordingRuntime("A", events));
        manager.addLast(new RecordingRuntime("B", events));

        manager.close();

        assertEquals(List.of("close:A", "close:B"), events);
    }

    @Test
    @DisplayName("addFirst/addLast 混用时列表序：addLast(A)、addFirst(B)、addLast(C) → B、A、C")
    void mixedRegistrationKeepsListOrder() {
        List<String> events = new ArrayList<>();
        ServerRuntimeManager manager = new ServerRuntimeManager();
        manager.addLast(new RecordingRuntime("A", events));
        manager.addFirst(new RecordingRuntime("B", events));
        manager.addLast(new RecordingRuntime("C", events));

        manager.close();

        assertEquals(List.of("close:B", "close:A", "close:C"), events);
    }

    @Test
    @DisplayName("注册 null 应用立即抛 NPE，addFirst/addLast 两个入口都不接受")
    void nullRuntimeIsRejectedByBothRegistrationMethods() {
        ServerRuntimeManager manager = new ServerRuntimeManager();

        NullPointerException first = assertThrows(NullPointerException.class, () -> manager.addFirst(null));
        assertEquals("The runtime must not be null", first.getMessage());
        NullPointerException last = assertThrows(NullPointerException.class, () -> manager.addLast(null));
        assertEquals("The runtime must not be null", last.getMessage());
    }

    @Test
    @DisplayName("close 中单个应用抛异常不阻断其余：异常应用之后的记录应用仍被关闭")
    void closeIsolatesFailingRuntimeAndKeepsClosingTheRest() {
        List<String> events = new ArrayList<>();
        ServerRuntimeManager manager = new ServerRuntimeManager();
        manager.addLast(new FailingRuntime("boom", events));
        manager.addLast(new RecordingRuntime("survivor", events));

        assertDoesNotThrow(manager::close);

        assertEquals(List.of("close:boom", "close:survivor"), events,
                "异常应用之后的其余应用必须继续关闭");
    }

    @Test
    @DisplayName("configure(null) 抛 NullPointerException")
    void configureRejectsNullPipeline() {
        ServerRuntimeManager manager = new ServerRuntimeManager();

        NullPointerException ex =
                assertThrows(NullPointerException.class, () -> manager.configure(null));

        assertEquals("pipeline can not be null", ex.getMessage());
    }
}
