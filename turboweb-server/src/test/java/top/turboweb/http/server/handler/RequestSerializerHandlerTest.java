package top.turboweb.http.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.Requests;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 连接级请求串行化的 L2 面：单请求直通、忙时入缓冲、响应结束回放、
 * 满 8 先关连接后仍入队（钉住形状）、inactive 清缓存、回放异常改道链尾不断连、环外调用移交回环执行。
 * 通道为 EmbeddedChannel：无真实 socket，事件由测试线程显式驱动；
 * 引用计数断言只用带体夹具（postJson）——无体请求的 EmptyByteBuf 引用计数恒 1，
 * 拿它断言释放是空转（Netty 4.2.12 EmptyByteBuf:1012-1044、DefaultFullHttpRequest:119-149）。
 */
class RequestSerializerHandlerTest {

    /** 记录进出双方向的末端替身；入站记录即释放（末端消费），出站原样传递。 */
    private static final class Fwd extends ChannelDuplexHandler {
        final List<Object> inbound = new ArrayList<>();
        final List<Object> outbound = new ArrayList<>();
        boolean throwOnSecondRead;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (throwOnSecondRead && inbound.size() == 1) {
                // 回放第二个请求时在下游炸掉（下游异常由 Netty 就地改道 exceptionCaught，不回穿调用点）
                throwOnSecondRead = false;
                ReferenceCountUtil.release(msg); // release 对 refCnt==0 幂等安全
                throw new IllegalStateException("downstream boom");
            }
            inbound.add(msg);
            ReferenceCountUtil.release(msg); // 末端替身：记录即消费，引用计数由测试侧收
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            outbound.add(msg);
            ctx.write(msg, promise);
        }
    }

    private EmbeddedChannel channel;
    private Fwd fwd;

    private void start() {
        fwd = new Fwd();
        channel = new EmbeddedChannel(new RequestSerializerHandler(), fwd);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("空闲时请求直通下游，并把连接置为处理中")
    void firstRequestPassesThroughAndMarksProcessing() {
        start();
        FullHttpRequest r1 = Requests.get("/a");
        channel.pipeline().fireChannelRead(r1);
        assertEquals(1, fwd.inbound.size(), "空闲请求必须直达下游");
        // 处理中标记：再来一个请求必须进缓冲而非下游
        FullHttpRequest r2 = Requests.get("/b");
        channel.pipeline().fireChannelRead(r2);
        assertEquals(1, fwd.inbound.size(), "isProcessing 期间第二个请求不得直达");
        Requests.release(r2); // r1 已被 Fwd 释放
    }

    @Test
    @DisplayName("响应写出 LastHttpContent 后，缓冲的下一个请求被回放到下游")
    void cachedRequestIsReplayedAfterResponseCompletes() {
        start();
        channel.pipeline().fireChannelRead(Requests.get("/a"));
        FullHttpRequest r2 = Requests.get("/b");
        channel.pipeline().fireChannelRead(r2);
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        channel.runPendingTasks(); // 释放任务经 eventLoop().execute 排队
        assertEquals(2, fwd.inbound.size(), "响应结束后缓冲请求必须回放");
    }

    @Test
    @DisplayName("非 FullHttpRequest 一律直通，不受串行化拦截")
    void nonFullRequestAlwaysPassesThrough() {
        start();
        channel.pipeline().fireChannelRead(Requests.get("/a")); // 置处理中
        LastHttpContent chunk = new io.netty.handler.codec.http.DefaultLastHttpContent();
        channel.pipeline().fireChannelRead(chunk);
        assertTrue(fwd.inbound.contains(chunk), "半包/终结内容不是 FullHttpRequest，不得被缓冲");
    }

    @Test
    @DisplayName("钉住：缓冲满 8 先关连接、随后仍入队第 9 个（Review Focus 2）")
    void cacheCapClosesChannelAndStillEnqueues() {
        start();
        channel.pipeline().fireChannelRead(Requests.get("/busy")); // 处理中
        List<FullHttpRequest> cached = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            // 带体夹具才有可观察引用计数：无体请求 EmptyByteBuf.refCnt 恒 1，断言会空转
            FullHttpRequest r = Requests.postJson("/q" + i, "{}");
            cached.add(r);
            channel.pipeline().fireChannelRead(r); // 前 8 个进缓冲；第 9 个撞 size>=8
        }
        assertEquals(1, fwd.inbound.size(), "第 9 个被 :40 return 截断，不得直达下游");
        assertFalse(channel.isActive(), "第 9 个（size>=8）触发 :36 ctx.close");
        for (int i = 0; i < 8; i++) {
            // EmbeddedChannel.close()（EmbeddedChannel:622-637）在调用内就把注销+inactive 任务跑完：
            // :76-85 兜底此刻已逐个释放先入队的 8 个
            assertEquals(0, cached.get(i).refCnt(), "先入队的 8 个必须被 close→inactive 兜底释放");
        }
        // Review Focus 2 钉住的形状：:36 先 close（其 inactive 派发在本调用内已跑完），
        // :38 随后仍入队第 9 个——它逃过这次唯一的兜底，滞留队列。
        // （EmbeddedChannel 急切驱动任务；真实 event loop 上注销任务排在当前读回调之后，
        // 第 9 个会先入队再被清——本例钉夹具可观察形状，不断言生产泄漏。）
        assertEquals(1, cached.get(8).refCnt(), "先关后入队的第 9 个逃过兜底（钉住 :36→:38 顺序）");
        Requests.release(cached.get(8)); // 测试侧收尾，避免 leak detector
    }

    @Test
    @DisplayName("显式 fire channelInactive：缓冲请求被 :76-85 逐个释放，且事件不传播、连接不受影响")
    void channelInactiveReleasesCachedRequests() {
        start();
        channel.pipeline().fireChannelRead(Requests.get("/busy"));
        FullHttpRequest cachedReq = Requests.postJson("/x", "{}"); // 带体夹具：释放可观察
        channel.pipeline().fireChannelRead(cachedReq);
        // 显式派发与 close 解耦：单独钉住 :76-85 的释放逻辑本身
        channel.pipeline().fireChannelInactive();
        assertEquals(0, cachedReq.refCnt(), "channelInactive 兜底必须逐个释放缓冲请求");
        assertTrue(channel.isActive(), "channelInactive 只清缓存：:85 无 super 调用，事件在此吞掉，不关连接");
    }

    @Test
    @DisplayName("HS-24 回归：回放期下游异常被 pipeline 截获改道 exceptionCaught 至链尾，异常不作断连依据（框架管理连接）")
    void replayFailureIsRoutedToExceptionCaughtChannelWithoutClosingChannel() {
        start();
        fwd.throwOnSecondRead = true;
        channel.pipeline().fireChannelRead(Requests.get("/a"));
        channel.pipeline().fireChannelRead(Requests.get("/b")); // 进缓冲，回放它在下游炸
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        channel.runPendingTasks();
        // Netty 对每次 handler 调用整体 try/catch（AbstractChannelHandlerContext:346-361），
        // 下游异常在抛出的 ctx 处改道 exceptionCaught、向链尾传播——从不回穿 serializer 的回放调用点；
        // 修复后回放处的 catch 是受检异常不得不捕获、捕获后交回 exceptionCaught 通道——
        // 异常不作为断连依据，连接生命周期由框架与通信两端管理。
        assertTrue(channel.isActive(), "修复形状：异常不断连，连接由框架与客户端管理");
        assertEquals(1, fwd.inbound.size(), "第二个请求没进过下游记录");
        // 异常没有蒸发：到达链尾后被 EmbeddedChannel 记账（EmbeddedChannelPipeline:1341-1344），
        // checkException 消费并重抛。必须在测试体内消费——否则 finishAndReleaseAll 会在 @AfterEach 里抛出。
        assertThrows(IllegalStateException.class, channel::checkException,
                "回放异常应以未处理 exceptionCaught 的形态到达链尾");
    }

    @Test
    @DisplayName("HS-24 回归：非本 channel 事件循环调用 channelRead 时移交回事件循环，不在调用线程触碰状态")
    void offEventLoopCallIsHandedBackToEventLoop() throws Exception {
        start();
        ChannelHandlerContext realCtx = channel.pipeline().context(RequestSerializerHandler.class);
        RequestSerializerHandler serializer = (RequestSerializerHandler) realCtx.handler();
        // EmbeddedChannel 的派发恒已在环内，环外分支从管线进不去：
        // 代理伪造 eventLoop 与 channel 上下文，inEventLoop 由开关控制、execute 入队由测试显式驱动
        AtomicBoolean onLoop = new AtomicBoolean(false);
        Deque<Runnable> pending = new ArrayDeque<>();
        EventLoop fakeLoop = (EventLoop) Proxy.newProxyInstance(
                EventLoop.class.getClassLoader(), new Class<?>[]{EventLoop.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "inEventLoop":
                            return onLoop.get();
                        case "execute":
                            pending.add((Runnable) args[0]);
                            return null;
                        default:
                            return method.invoke(realCtx.channel().eventLoop(), args);
                    }
                });
        Channel fakeChannel = (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(), new Class<?>[]{Channel.class},
                (proxy, method, args) -> "eventLoop".equals(method.getName())
                        ? fakeLoop
                        : method.invoke(realCtx.channel(), args));
        ChannelHandlerContext proxyCtx = (ChannelHandlerContext) Proxy.newProxyInstance(
                ChannelHandlerContext.class.getClassLoader(), new Class<?>[]{ChannelHandlerContext.class},
                (proxy, method, args) -> "channel".equals(method.getName())
                        ? fakeChannel
                        : method.invoke(realCtx, args));
        FullHttpRequest deferred = Requests.get("/defer");
        serializer.channelRead(proxyCtx, deferred); // 模拟环外线程直调
        assertEquals(0, fwd.inbound.size(), "环外调用不得直达下游，须移交事件循环");
        assertEquals(1, pending.size(), "控制权应以单个任务移交回本 channel 的事件循环");
        onLoop.set(true);
        pending.forEach(Runnable::run); // 环内驱动移交任务
        assertEquals(1, fwd.inbound.size(), "移交任务在环内执行后请求应到达下游");
    }
}
