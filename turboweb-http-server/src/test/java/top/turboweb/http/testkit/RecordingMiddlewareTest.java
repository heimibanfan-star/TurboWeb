package top.turboweb.http.testkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 夹具自证：记录件的链式行为与事件顺序。
 */
class RecordingMiddlewareTest {

    @Test
    @DisplayName("无载荷时向后传递并记录invoke，有载荷时直接返回载荷")
    void recordsInvokeAndReturnsPayload() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware head = new RecordingMiddleware("head", events);
        RecordingMiddleware tail = new RecordingMiddleware("tail", events, "body");

        // 与装配期一致：先串链，再逐个 init(链头)
        head.setNext(tail);
        head.init(head);
        tail.init(head);

        assertEquals("body", head.invoke(null), "head 无载荷应向后走到 tail 取其载荷");
        assertEquals(List.of("init:head", "init:tail", "invoke:head", "invoke:tail"), events);
    }

    @Test
    @DisplayName("链尾无 next 时 invoke 返回 null 而不是抛异常")
    void invokeWithoutNextReturnsNull() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware alone = new RecordingMiddleware("alone", events);

        assertNull(alone.invoke(null));
        assertEquals(List.of("invoke:alone"), events);
    }

    @Test
    @DisplayName("哨兵与记录件混排时事件顺序仍为链头到链尾")
    void sentinelAndRecordingChainOrder() {
        List<String> events = new ArrayList<>();
        Middleware sentinel = new SentinelMiddleware();
        RecordingMiddleware first = new RecordingMiddleware("first", events);
        RecordingMiddleware second = new RecordingMiddleware("second", events);

        sentinel.setNext(first);
        first.setNext(second);
        first.init(sentinel);
        second.init(sentinel);

        assertEquals(List.of("init:first", "init:second"), events);
        first.destroy();
        second.destroy();
        assertEquals(List.of("init:first", "init:second", "destroy:first", "destroy:second"), events);
    }
}
