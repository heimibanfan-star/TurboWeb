package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.ReactorResponse;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reactor 策略的写出路径（L2）。doHandle 首行钉死虚拟线程契约，
 * 全部经 handle() 在自起虚拟线程里执行、join(5000) 等返回值——
 * 流由 Flux.just/error 驱动、通道是 EmbeddedChannel，链路同步可断言。
 */
class ReactorResponseStrategyTest {

    /** 在虚拟线程里跑 body，返回其结果；超时未返回即红（不拖死 worker：守护语义由 join 上界保证）。 */
    private static ChannelFuture handleOnVirtualThread(ResponseStrategy strategy,
                                                      HttpResponse response,
                                                      InternalConnectSession session) throws InterruptedException {
        AtomicReference<ChannelFuture> box = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                box.set(strategy.handle(response, session));
            } catch (Throwable te) {
                err.set(te);
            }
        });
        t.join(5000);
        assertFalse(t.isAlive(), "handle 必须及时返回（链路同步）");
        assertNull(err.get(), "handle 模板方法不得让异常逃逸");
        return box.get();
    }

    private static List<Object> drain(EmbeddedChannel channel) {
        List<Object> out = new ArrayList<>();
        Object m;
        while ((m = channel.readOutbound()) != null) {
            out.add(m);
        }
        return out;
    }

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("正常流：chunked 头 + 逐块内容 + LAST，promise 成功")
    void happyPathWritesHeaderChunksAndLast() throws InterruptedException {
        ReactorResponse resp = new ReactorResponse(Flux.just(buf("ab"), buf("cd")));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        try {
            ChannelFuture f = handleOnVirtualThread(new ReactorResponseStrategy(false), resp, session);
            assertTrue(f.isSuccess());
            assertEquals("chunked", resp.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
            assertEquals("keep-alive", resp.headers().get(HttpHeaderNames.CONNECTION));
            List<Object> out = drain(channel);
            assertSame(resp, out.get(0));
            assertInstanceOf(DefaultHttpContent.class, out.get(1));
            assertInstanceOf(DefaultHttpContent.class, out.get(2));
            assertInstanceOf(LastHttpContent.class, out.get(3));
            assertEquals(4, out.size());
            for (Object m : out) {
                ReferenceCountUtil.release(m); // 含 Unpooled 堆缓冲内容块与头本身
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("enableLimit=true：promise 同步完成后 latch 立即放行，返回值不变")
    void enableLimitReturnsAfterPromiseDoneOnSameFuture() throws InterruptedException {
        ReactorResponse resp = new ReactorResponse(Flux.just(buf("x")));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        try {
            ChannelFuture f = handleOnVirtualThread(new ReactorResponseStrategy(true), resp, session);
            assertTrue(f.isSuccess(), "限流路径等待的正是同一 promise");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("流异常：promise 失败于原异常，close 任务经 runPendingTasks 关掉通道")
    void fluxErrorFailsPromiseAndClosesSession() throws InterruptedException {
        IllegalStateException boom = new IllegalStateException("boom");
        ReactorResponse resp = new ReactorResponse(Flux.error(boom));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        try {
            ChannelFuture f = handleOnVirtualThread(new ReactorResponseStrategy(false), resp, session);
            assertSame(boom, f.cause());
            channel.runPendingTasks(); // session.close() 排入 eventLoop
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("响应头写失败（通道已关）：promise 置败，不抛穿 handle")
    void headerWriteFailureSetsPromiseFailure() throws InterruptedException {
        ByteBuf ab = buf("ab"); // 头写失败则流永不订阅，该缓冲只能测试侧回收
        ReactorResponse resp = new ReactorResponse(Flux.just(ab));
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalConnectSession session = new InternalConnectSession(channel);
        channel.close().sync();
        try {
            ChannelFuture f = handleOnVirtualThread(new ReactorResponseStrategy(false), resp, session);
            assertFalse(f.isSuccess(), "写头失败必须走 :80-81 置败分支");
            // 终审修复#1：pending promise 同样 isSuccess()==false——必须钉终态与真实 cause，
            // 否则删掉 :80 setFailure 本用例仍绿。（:81 在"预关通道"夹具形状下不可观察：
            // InternalConnectSession.close() 对已关通道 early-return，本用例只钉得住 :80。）
            assertTrue(f.isDone(), "promise 必须经 :80 setFailure 到达终态（pending 也会让 isSuccess()==false 通过）");
            assertNotNull(f.cause(), "置败必须携带写头失败的真实 cause");
            channel.runPendingTasks();
        } finally {
            ReferenceCountUtil.release(ab);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("虚拟线程里喂错类型：IAE 被模板兜成 500 错误页")
    void wrongTypeOnVirtualThreadGives500() throws InterruptedException {
        DefaultFullHttpResponse plain =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            ChannelFuture f = handleOnVirtualThread(
                    new ReactorResponseStrategy(false), plain, new InternalConnectSession(channel));
            assertTrue(f.isSuccess());
            List<Object> out = drain(channel);
            DefaultFullHttpResponse err = assertInstanceOf(DefaultFullHttpResponse.class, out.get(0));
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, err.status());
            plain.release();
            err.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("平台线程直接调用：:72 虚拟线程断言被模板兜成 500（Review Focus 4·Reactor 半边）")
    void platformThreadIsRejectedAs500() {
        ByteBuf z = buf("z"); // 断言先于订阅抛出，该缓冲只能测试侧回收
        ReactorResponse resp = new ReactorResponse(Flux.just(z));
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            // 就在当前（JUnit 平台）线程直调 handle，不自起虚拟线程
            ChannelFuture f = new ReactorResponseStrategy(false)
                    .handle(resp, new InternalConnectSession(channel));
            assertTrue(f.isSuccess(), "模板 catch 的返回 future 是 500 页写出 future，应成功");
            List<Object> out = drain(channel);
            assertEquals(1, out.size(), "唯一出站帧 = 模板兜底的错误页（断言若被删除会先写出响应头）");
            DefaultFullHttpResponse err = assertInstanceOf(DefaultFullHttpResponse.class, out.get(0));
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, err.status());
            err.release();
        } finally {
            ReferenceCountUtil.release(z);
            channel.finishAndReleaseAll();
        }
    }
}
