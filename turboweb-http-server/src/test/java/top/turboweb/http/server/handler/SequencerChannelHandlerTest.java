package top.turboweb.http.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import top.turboweb.http.connection.SequencedDataChunk;
import top.turboweb.http.connection.SequencedRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import io.netty.util.concurrent.EventExecutor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SequencerChannelHandler} 的响应定序行为锚：
 * 请求序列号与其写出游标一致的响应分块即时透传，提前写出的响应整体缓冲，
 * 前序终止块写出后按序列顺序冲刷缓冲；非封装消息一律透传。
 * <p>写出经显式凭据驱动，断言凭据在缓冲期间悬置、冲刷或断连后落定。</p>
 */
class SequencerChannelHandlerTest {

    /** 每条用例新建：顺序处理器持有自增序列分配器，供模拟调度器分配序列号 */
    private EmbeddedChannel newChannel(int maxInFlight) {
        return new EmbeddedChannel(new SequencerChannelHandler(maxInFlight));
    }

    /** 派发一条入站请求，返回定序处理器为其分配的序列号，请求对象随之释放 */
    private static long dispatchRequest(EmbeddedChannel channel, String uri) {
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
        SequencedRequest sequenced = (SequencedRequest) channel.readInbound();
        long sequence = sequenced.sequence();
        sequenced.request().release();
        return sequence;
    }

    /** 写出一条序列化消息，返回其写出凭据 */
    private static ChannelPromise writeOut(EmbeddedChannel channel, Object message) {
        ChannelPromise promise = channel.newPromise();
        channel.pipeline().writeAndFlush(message, promise);
        return promise;
    }

    private static SequencedDataChunk sequenced(long seq, Object message) {
        return new SequencedDataChunk(seq, message);
    }

    private static DefaultHttpResponse response(String marker) {
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set("X-Marker", marker);
        return response;
    }

    private static DefaultHttpContent content(String text) {
        return new DefaultHttpContent(Unpooled.copiedBuffer(text, StandardCharsets.UTF_8));
    }

    /** 凭据是否以失败落定 */
    private static boolean failed(ChannelPromise promise) {
        return promise.isDone() && !promise.isSuccess();
    }

    // ---------- 同序直写 ----------

    @Test
    void inOrderWritesPassThroughImmediately() {
        EmbeddedChannel channel = newChannel(8);
        long seq = dispatchRequest(channel, "/a");

        writeOut(channel, sequenced(seq, response("head")));
        ChannelPromise bodyFuture = writeOut(channel, sequenced(seq, content("body")));
        writeOut(channel, sequenced(seq, LastHttpContent.EMPTY_LAST_CONTENT));

        assertSameMarker(channel.readOutbound(), "head");
        DefaultHttpContent body = assertInstanceOf(DefaultHttpContent.class, channel.readOutbound());
        assertEquals("body", body.content().toString(StandardCharsets.UTF_8));
        body.release();
        assertInstanceOf(LastHttpContent.class, channel.readOutbound(), "终止块即时透传");
        assertNull(channel.readOutbound(), "无缓冲积压");
        assertTrue(bodyFuture.isSuccess(), "同序写出凭据即时落定");
        channel.finishAndReleaseAll();
    }

    // ---------- 乱序缓冲与冲刷 ----------

    @Test
    void outOfOrderResponseIsBufferedUntilPredecessorFinishes() {
        EmbeddedChannel channel = newChannel(8);
        long first = dispatchRequest(channel, "/1");
        long second = dispatchRequest(channel, "/2");

        // 序列2提前写出：整体缓冲，不出管道
        writeOut(channel, sequenced(second, response("second-head")));
        assertNull(channel.readOutbound(), "未轮到写出的响应缓冲不出管道");

        // 序列1写出终止块：游标推进后冲刷序列2
        writeOut(channel, sequenced(first, response("first-head")));
        writeOut(channel, sequenced(first, LastHttpContent.EMPTY_LAST_CONTENT));

        assertSameMarker(channel.readOutbound(), "first-head");
        assertInstanceOf(LastHttpContent.class, channel.readOutbound());
        assertSameMarker(channel.readOutbound(), "second-head");
        assertNull(channel.readOutbound());
        channel.finishAndReleaseAll();
    }

    @Test
    void bufferedChunksOfSameSequenceKeepWriteOrder() {
        EmbeddedChannel channel = newChannel(8);
        long first = dispatchRequest(channel, "/1");
        long second = dispatchRequest(channel, "/2");

        // 序列2分块提前写出：链表按写出序缓冲
        writeOut(channel, sequenced(second, response("head")));
        writeOut(channel, sequenced(second, content("chunk-1")));
        writeOut(channel, sequenced(second, content("chunk-2")));
        writeOut(channel, sequenced(second, LastHttpContent.EMPTY_LAST_CONTENT));

        writeOut(channel, sequenced(first, LastHttpContent.EMPTY_LAST_CONTENT));

        assertInstanceOf(LastHttpContent.class, channel.readOutbound());
        assertSameMarker(channel.readOutbound(), "head");
        DefaultHttpContent chunk1 = assertInstanceOf(DefaultHttpContent.class, channel.readOutbound());
        assertEquals("chunk-1", chunk1.content().toString(StandardCharsets.UTF_8));
        chunk1.release();
        DefaultHttpContent chunk2 = assertInstanceOf(DefaultHttpContent.class, channel.readOutbound());
        assertEquals("chunk-2", chunk2.content().toString(StandardCharsets.UTF_8));
        chunk2.release();
        assertInstanceOf(LastHttpContent.class, channel.readOutbound(), "缓冲响应的终止块最后冲刷");
        channel.finishAndReleaseAll();
    }

    @Test
    void bufferedWriteCompletesOnlyAfterFlushedOut() {
        EmbeddedChannel channel = newChannel(8);
        long first = dispatchRequest(channel, "/1");
        long second = dispatchRequest(channel, "/2");

        // 缓冲中的写出凭据悬置
        ChannelPromise bufferedFuture = writeOut(channel, sequenced(second, response("head")));
        assertFalse(bufferedFuture.isDone(), "缓冲期间写出凭据不完成");

        writeOut(channel, sequenced(first, LastHttpContent.EMPTY_LAST_CONTENT));

        assertTrue(bufferedFuture.isSuccess(), "冲刷写出后凭据完成");
        channel.finishAndReleaseAll();
    }

    // ---------- 差值断连 ----------

    @Test
    void dispatchBeyondWindowClosesChannelAndDropsRequest() {
        EmbeddedChannel channel = newChannel(2);
        DefaultFullHttpRequest first = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/1");
        DefaultFullHttpRequest second = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/2");
        DefaultFullHttpRequest third = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/3");

        channel.writeInbound(first);
        channel.writeInbound(second);
        channel.writeInbound(third);

        channel.runPendingTasks();
        assertFalse(channel.isOpen(), "在途请求数超过阈值断开连接");
        assertEquals(0, third.refCnt(), "超窗请求已释放不派发");
        assertInstanceOf(SequencedRequest.class, channel.readInbound(), "窗口内的请求正常派发");
        assertInstanceOf(SequencedRequest.class, channel.readInbound());
        assertNull(channel.readInbound(), "超窗请求不向下游派发");
    }

    @Test
    void inboundAfterCloseIsReleasedNotForwarded() throws Exception {
        SequencerChannelHandler handler = new SequencerChannelHandler(8);
        List<Object> fired = new ArrayList<>();
        ChannelHandlerContext ctx = (ChannelHandlerContext) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ChannelHandlerContext.class},
                (proxy, method, args) -> {
                    if ("fireChannelRead".equals(method.getName())) {
                        fired.add(args[0]);
                        return proxy;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    return null;
                });

        // 通道失活置位断连标记后，残余流入的请求直接释放不派发
        handler.channelInactive(ctx);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/late");

        handler.channelRead(ctx, request);

        assertEquals(0, request.refCnt(), "断连后到达的请求直接释放");
        assertTrue(fired.isEmpty(), "断连后到达的请求不向下游派发");
    }

    // ---------- 序列分配器溢出 ----------

    @Test
    void allocatorOverflowClosesChannelAndDropsRequest() throws Exception {
        EmbeddedChannel channel = newChannel(8);
        // 经反射把序列分配器拨到Long上界，下一次分配即回绕溢出
        Field allocator = SequencerChannelHandler.class.getDeclaredField("sequenceAllocator");
        allocator.setAccessible(true);
        allocator.setLong(channel.pipeline().get(SequencerChannelHandler.class), Long.MAX_VALUE);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/overflow");
        channel.writeInbound(request);

        channel.runPendingTasks();
        assertFalse(channel.isOpen(), "序列号分配器溢出断开连接");
        assertEquals(0, request.refCnt(), "溢出时到达的请求已释放不派发");
        assertNull(channel.readInbound(), "溢出时到达的请求不向下游派发");
    }

    // ---------- 连接关闭释放 ----------

    @Test
    void channelInactiveReleasesBufferedContentAndFailsWrites() {
        EmbeddedChannel channel = newChannel(8);
        long first = dispatchRequest(channel, "/1");
        long second = dispatchRequest(channel, "/2");

        writeOut(channel, sequenced(first, response("first")));
        ChannelPromise bufferedFuture = writeOut(channel, sequenced(second, content("buffered")));

        channel.close().awaitUninterruptibly();

        assertTrue(failed(bufferedFuture), "缓冲中的写凭据以失败收场");
        assertSameMarker(channel.readOutbound(), "first", "失活前已透传的消息不受影响");
        assertFalse(channel.finishAndReleaseAll(), "缓冲内容已随失活释放，无残留出站消息");
    }

    @Test
    void closeFailsLaterWritesOfBufferedSequence() {
        EmbeddedChannel channel = newChannel(8);
        long first = dispatchRequest(channel, "/1");
        long second = dispatchRequest(channel, "/2");

        writeOut(channel, sequenced(first, response("first")));
        writeOut(channel, sequenced(second, content("buffered")));

        channel.close().awaitUninterruptibly();

        ChannelPromise lateFuture = writeOut(channel, sequenced(second, content("late")));
        assertTrue(failed(lateFuture), "断连后到达的写出凭据直接失败");
    }

    // ---------- 入站封章 ----------

    @Test
    void inboundRequestIsSequencedAndForwarded() {
        EmbeddedChannel channel = newChannel(8);
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/a");

        channel.writeInbound(request);

        SequencedRequest sequenced = assertInstanceOf(SequencedRequest.class, channel.readInbound(),
                "入站请求封章携带序列号向下游派发");
        assertEquals(1, sequenced.sequence(), "首个请求序列号为1");
        assertSame(request, sequenced.request(), "封章承载原请求对象");
        channel.finishAndReleaseAll();
    }

    @Test
    void upgradeRequestPassesThroughUnsequenced() {
        EmbeddedChannel channel = newChannel(8);
        DefaultFullHttpRequest upgrade = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws");
        upgrade.headers().set("Upgrade", "websocket");

        channel.writeInbound(upgrade);

        assertSame(upgrade, channel.readInbound(), "升级协议请求不封章原样透传");

        DefaultFullHttpRequest normal = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/a");
        channel.writeInbound(normal);
        SequencedRequest sequenced = assertInstanceOf(SequencedRequest.class, channel.readInbound());
        assertEquals(1, sequenced.sequence(), "升级协议请求不占用序列号");
        channel.finishAndReleaseAll();
    }

    // ---------- 线程语义 ----------

    @Test
    void writeFromNonEventLoopThreadIsSubmittedToEventLoop() throws Exception {
        DefaultEventLoopGroup group = new DefaultEventLoopGroup(1);
        try {
            EventExecutor eventLoop = group.next();
            List<Thread> writeThreads = new ArrayList<>();
            ChannelHandlerContext ctx = (ChannelHandlerContext) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{ChannelHandlerContext.class},
                    (proxy, method, args) -> {
                        if ("executor".equals(method.getName())) {
                            return eventLoop;
                        }
                        if ("write".equals(method.getName())) {
                            writeThreads.add(Thread.currentThread());
                            return null;
                        }
                        Class<?> rt = method.getReturnType();
                        if (rt == boolean.class) {
                            return false;
                        }
                        return null;
                    });
            EmbeddedChannel channel = new EmbeddedChannel();
            try {
                // 主线程（非eventLoop线程）调用写出：定序裁决须提交eventLoop执行
                new SequencerChannelHandler(8).write(ctx, sequenced(1, response("head")), channel.newPromise());
                // FIFO：本标记任务执行完时，先于它提交的写任务必已执行
                eventLoop.submit(() -> {
                }).sync();

                assertEquals(1, writeThreads.size());
                assertTrue(eventLoop.inEventLoop(writeThreads.get(0)), "非eventLoop线程的写出提交eventLoop执行");
            } finally {
                channel.finishAndReleaseAll();
            }
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    // ---------- 非封装消息透传 ----------

    @Test
    void nonSequencedMessagesPassThroughUntouched() {
        EmbeddedChannel channel = newChannel(8);

        writeOut(channel, response("plain"));

        // 升级协议握手等非封装消息不经定序，原样透传
        assertSameMarker(channel.readOutbound(), "plain");
        channel.finishAndReleaseAll();
    }

    /** 断言出站消息为携带标记头的响应 */
    private static void assertSameMarker(Object message, String marker) {
        assertSameMarker(message, marker, null);
    }

    /** 断言出站消息为携带标记头的响应，message 为 null 时从出站队列读取 */
    private static void assertSameMarker(Object message, String marker, String note) {
        DefaultHttpResponse response = assertInstanceOf(DefaultHttpResponse.class, message, note);
        assertEquals(marker, response.headers().get("X-Marker"), note);
        ReferenceCountUtil.release(message);
    }
}
