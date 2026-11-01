package top.turboweb.http.response;

import org.junit.jupiter.api.Test;
import top.turboweb.http.core.StatusLine;
import top.turboweb.http.exception.SseBufferException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SseEmitterPlus} 的行为锚：默认缓冲容量 32 且可选构造指定容量、
 * 非正容量拒收、各发送方法的字段包裹格式、结束信号终止流且拒绝后续投递、
 * 关闭监听器登记可读出、默认响应头与状态行口径。
 */
class SseEmitterPlusTest {

    @Test
    void defaultBufferCapacityIs32() {
        SseEmitterPlus emitter = new SseEmitterPlus();

        for (int i = 0; i < 32; i++) {
            emitter.send("m" + i);
        }

        assertThrows(SseBufferException.class, () -> emitter.send("overflow"),
                "默认容量 32，第 33 条事件投递时缓冲已满");
    }

    @Test
    void customBufferCapacityIsHonored() {
        SseEmitterPlus emitter = new SseEmitterPlus(5);

        for (int i = 0; i < 5; i++) {
            emitter.send("m" + i);
        }

        assertThrows(SseBufferException.class, () -> emitter.send("overflow"),
                "可选构造指定容量 5，第 6 条事件投递时缓冲已满");
    }

    @Test
    void nonPositiveBufferCapacityRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SseEmitterPlus(0));
        assertThrows(IllegalArgumentException.class, () -> new SseEmitterPlus(-1));
    }

    @Test
    void sendDataWrapsAsDataField() {
        SseEmitterPlus emitter = new SseEmitterPlus();

        emitter.sendData("hello");
        emitter.complete();

        List<String> received = emitter.content().asFlux().collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("data:hello\n\n"), received);
    }

    @Test
    void sendHelpersWrapTheirField() {
        SseEmitterPlus emitter = new SseEmitterPlus();

        emitter.send("raw");
        emitter.sendEvent("evt");
        emitter.sendId("42");
        emitter.sendRetry(3000);
        emitter.sendComment("ping");
        emitter.complete();

        List<String> received = emitter.content().asFlux().collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("raw", "event:evt\n\n", "id:42\n\n", "retry:3000\n\n", ":ping\n\n"), received);
    }

    @Test
    void completeEndsStreamAndRejectsFurtherEmission() {
        SseEmitterPlus emitter = new SseEmitterPlus();

        emitter.sendData("a");
        emitter.complete();

        List<String> received = emitter.content().asFlux().collectList().block(Duration.ofSeconds(5));
        assertEquals(List.of("data:a\n\n"), received);
        assertThrows(IllegalStateException.class, () -> emitter.send("late"),
                "结束后再投递事件拒收");
        assertThrows(IllegalStateException.class, () -> emitter.complete(),
                "结束后再次结束信号拒收");
    }

    @Test
    void closeListenersAreExposedForWriter() {
        SseEmitterPlus emitter = new SseEmitterPlus();
        AtomicBoolean fired = new AtomicBoolean(false);

        emitter.addCloseListener(() -> fired.set(true));

        assertEquals(1, emitter.closeListeners().size(), "登记的关闭监听器可被写出器读出");
        emitter.closeListeners().forEach(Runnable::run);
        assertTrue(fired.get());
    }

    @Test
    void defaultHeadersCarryEventStreamContract() {
        SseEmitterPlus emitter = new SseEmitterPlus();

        assertEquals("text/event-stream", emitter.header("Content-Type"),
                "默认内容类型为事件流，charset 由结果转化器后续补齐");
        assertEquals("no-cache", emitter.header("Cache-Control"));
        assertEquals("keep-alive", emitter.header("Connection"));
    }

    @Test
    void statusLineIsOk() {
        assertEquals(StatusLine.OK, new SseEmitterPlus().statusLine());
    }
}
