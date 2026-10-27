package top.turboweb.http.middleware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.RecordingMiddleware;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BaseMiddleware} / {@link Middleware} / {@link SentinelMiddleware} 的链式语义（spec §5：链顺序与 pre/post 语义）。
 *
 * <p>本类只验证"链怎么串、锁住之后还能不能改、链尾怎么收"这三件事。所有用例的 {@code ctx} 均为
 * {@code null}：这几个类只把 ctx 透传给后继，不读写它（见任务章"ctx 取值说明"）。
 */
class BaseMiddlewareTest {

    @Test
    @DisplayName("setNext 存下后继，getNext 原样取回")
    void setNextStoresNextAndGetNextReturnsIt() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware a = new RecordingMiddleware("a", events);
        RecordingMiddleware b = new RecordingMiddleware("b", events);

        a.setNext(b);

        assertSame(b, a.getNext());
    }

    @Test
    @DisplayName("未锁定时 setNext 覆盖旧后继")
    void setNextReplacesPreviousNextBeforeLock() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware a = new RecordingMiddleware("a", events);
        RecordingMiddleware b = new RecordingMiddleware("b", events);
        RecordingMiddleware c = new RecordingMiddleware("c", events);

        a.setNext(b);
        a.setNext(c);

        assertSame(c, a.getNext(), "最后一次 setNext 生效");
    }

    @Test
    @DisplayName("观察：BaseMiddleware:20-26 lockMiddleware 之后 setNext 只告警、不改链")
    void lockedSetNextIsIgnored() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware a = new RecordingMiddleware("a", events);
        RecordingMiddleware b = new RecordingMiddleware("b", events);
        RecordingMiddleware c = new RecordingMiddleware("c", events);

        a.setNext(b);
        a.lockMiddleware();
        a.setNext(c);

        assertSame(b, a.getNext(), "锁定后链保持不变——setNext 落到 if (isLock) 的告警分支");
    }

    @Test
    @DisplayName("Middleware.next 委托后继并逐级返回载荷")
    void middlewareNextDelegatesToNextInChain() {
        List<String> events = new ArrayList<>();
        RecordingMiddleware head = new RecordingMiddleware("head", events);
        head.setNext(new RecordingMiddleware("tail", events, "body"));

        assertEquals("body", head.invoke(null));
        assertEquals(List.of("invoke:head", "invoke:tail"), events);
    }

    @Test
    @DisplayName("Middleware.next 在 getNext()==null 时返回 null 而不是抛异常")
    void middlewareNextReturnsNullWhenChainEnds() {
        SentinelMiddleware sentinel = new SentinelMiddleware();

        assertNull(sentinel.invoke(null), "链尾直接返回 null");
    }

    @Test
    @DisplayName("哨兵中间件把请求原样交给后继")
    void sentinelDelegatesToItsNext() {
        List<String> events = new ArrayList<>();
        SentinelMiddleware sentinel = new SentinelMiddleware();
        sentinel.setNext(new RecordingMiddleware("tail", events, "body"));

        assertEquals("body", sentinel.invoke(null));
        assertEquals(List.of("invoke:tail"), events);
    }

    @Test
    @DisplayName("BaseMiddleware 的默认 init/destroy 是空实现，不动链也不触发回调")
    void defaultInitAndDestroyLeaveTheChainUntouched() {
        List<String> events = new ArrayList<>();
        SentinelMiddleware sentinel = new SentinelMiddleware();
        RecordingMiddleware tail = new RecordingMiddleware("tail", events);
        sentinel.setNext(tail);

        sentinel.init(null);
        sentinel.destroy();

        assertSame(tail, sentinel.getNext(), "默认钩子不得改动链结构");
        assertTrue(events.isEmpty(), "默认钩子不得触发链上任何回调");
    }
}
