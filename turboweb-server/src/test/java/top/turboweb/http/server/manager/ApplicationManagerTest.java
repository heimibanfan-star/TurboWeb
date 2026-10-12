package top.turboweb.http.server.manager;

import io.netty.channel.ChannelPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.utils.resource.ScopedResource;
import top.turboweb.http.server.application.Application;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ApplicationManager} 的注册顺序、null 校验与关闭隔离（spec §5：server/manager）。
 *
 * <p>装配顺序（{@link ApplicationManager#configure(ChannelPipeline)} 的调用序）与关闭顺序共用
 * 同一个注册列表，而 configure 需要一条真实的 {@link ChannelPipeline}（属 L2，需 EmbeddedChannel），
 * 因此这里以 {@code close()} 的调用序作为列表顺序的 L1 可观测面；configure 的非 null 路径由
 * {@code ServerChannelInitializerTest} 经 EmbeddedChannel 覆盖。
 */
class ApplicationManagerTest {

    /** 记录型应用替身：configure/close 各落一条事件，用于观察注册顺序。 */
    private static final class RecordingApplication implements Application {

        private final String name;
        private final List<String> events;

        RecordingApplication(String name, List<String> events) {
            this.name = name;
            this.events = events;
        }

        @Override
        public void configure(ChannelPipeline pipeline) {
            events.add("configure:" + name);
        }

        @Override
        public ScopedResource getScopedResource() {
            return new ScopedResource();
        }

        @Override
        public void close() {
            events.add("close:" + name);
        }
    }

    /** 关闭必抛的应用替身：先记录再抛，用来验证单个应用失败不阻断其余应用。 */
    private static final class FailingApplication implements Application {

        private final String name;
        private final List<String> events;

        FailingApplication(String name, List<String> events) {
            this.name = name;
            this.events = events;
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
            events.add("close:" + name);
            throw new IllegalStateException("模拟应用关闭失败");
        }
    }

    @Test
    @DisplayName("addFirst 插到队头：先 addLast(A) 再 addFirst(B)，关闭序为 B、A")
    void addFirstInsertsBeforePreviouslyRegisteredApplications() {
        List<String> events = new ArrayList<>();
        ApplicationManager manager = new ApplicationManager();
        manager.addLast(new RecordingApplication("A", events));
        manager.addFirst(new RecordingApplication("B", events));

        manager.close();

        assertEquals(List.of("close:B", "close:A"), events);
    }

    @Test
    @DisplayName("addLast 排到队尾：addLast(A) 后 addLast(B)，关闭序为 A、B")
    void addLastAppendsAfterPreviouslyRegisteredApplications() {
        List<String> events = new ArrayList<>();
        ApplicationManager manager = new ApplicationManager();
        manager.addLast(new RecordingApplication("A", events));
        manager.addLast(new RecordingApplication("B", events));

        manager.close();

        assertEquals(List.of("close:A", "close:B"), events);
    }

    @Test
    @DisplayName("addFirst/addLast 混用时列表序：addLast(A)、addFirst(B)、addLast(C) → B、A、C")
    void mixedRegistrationKeepsListOrder() {
        List<String> events = new ArrayList<>();
        ApplicationManager manager = new ApplicationManager();
        manager.addLast(new RecordingApplication("A", events));
        manager.addFirst(new RecordingApplication("B", events));
        manager.addLast(new RecordingApplication("C", events));

        manager.close();

        assertEquals(List.of("close:B", "close:A", "close:C"), events);
    }

    @Test
    @DisplayName("注册 null 应用立即抛 NPE，addFirst/addLast 两个入口都不接受")
    void nullApplicationIsRejectedByBothRegistrationMethods() {
        ApplicationManager manager = new ApplicationManager();

        NullPointerException first = assertThrows(NullPointerException.class, () -> manager.addFirst(null));
        assertEquals("应用不能为null", first.getMessage());
        NullPointerException last = assertThrows(NullPointerException.class, () -> manager.addLast(null));
        assertEquals("应用不能为null", last.getMessage());
    }

    @Test
    @DisplayName("close 中单个应用抛异常不阻断其余：异常应用之后的记录应用仍被关闭")
    void closeIsolatesFailingApplicationAndKeepsClosingTheRest() {
        List<String> events = new ArrayList<>();
        ApplicationManager manager = new ApplicationManager();
        manager.addLast(new FailingApplication("boom", events));
        manager.addLast(new RecordingApplication("survivor", events));

        assertDoesNotThrow(manager::close);

        assertEquals(List.of("close:boom", "close:survivor"), events,
                "异常应用之后的其余应用必须继续关闭");
    }

    @Test
    @DisplayName("configure(null) 抛 IllegalArgumentException")
    void configureRejectsNullPipeline() {
        ApplicationManager manager = new ApplicationManager();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> manager.configure(null));

        assertEquals("pipeline can not be null", ex.getMessage());
    }
}
