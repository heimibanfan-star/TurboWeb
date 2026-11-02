package top.turboweb.http.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.Test;
import top.turboweb.concurrent.thread.ElasticMicroTaskExecutor;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.core.StatusLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InternalHttpConnection} 的写出通道行为锚：
 * 首部、响应体与终止块分别以 Netty 响应消息、内容块与终止块对应写出，
 * 异步凭据在写出结果落定后正常完成。
 */
class InternalHttpConnectionTest {

    @Test
    void writeHeadersEmitsResponseWithStatusAndHeaders() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", List.of("text/html;charset=utf-8"));
        headers.put("X-Multi", List.of("a", "b"));

        connection.writeHeaders(StatusLine.OK, headers).get(5, TimeUnit.SECONDS);

        HttpResponse response = channel.readOutbound();
        assertEquals(HttpVersion.HTTP_1_1, response.protocolVersion());
        assertEquals(200, response.status().code());
        assertEquals("OK", response.status().reasonPhrase());
        assertEquals("text/html;charset=utf-8", response.headers().get("Content-Type"));
        assertEquals(List.of("a", "b"), response.headers().getAll("X-Multi"), "同名头取值逐条写出");
        channel.finishAndReleaseAll();
    }

    @Test
    void writeEmitsBodyContentBytes() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        byte[] body = "hello".getBytes();

        connection.write(body).get(5, TimeUnit.SECONDS);

        DefaultHttpContent content = channel.readOutbound();
        byte[] written = new byte[content.content().readableBytes()];
        content.content().readBytes(written);
        assertArrayEquals(body, written, "响应体按入参字节原样写出");
        content.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void writeCarriesCallerArrayWithoutCopy() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        byte[] body = "hello".getBytes();

        connection.write(body).get(5, TimeUnit.SECONDS);

        DefaultHttpContent content = channel.readOutbound();
        assertTrue(content.content().hasArray(), "写出内容由堆数组承载");
        assertSame(body, content.content().array(), "写出零拷贝：直接承载入参数组，不做复制");
        content.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void finishWriteEmitsTerminalLastContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);

        connection.finishWrite().get(5, TimeUnit.SECONDS);

        assertInstanceOf(LastHttpContent.class, channel.readOutbound(), "结束标记以终止块写出");
        channel.finishAndReleaseAll();
    }

    // ---------- 卸载完成 ----------

    @Test
    void offloadedCredentialsCompleteOnOffloadExecutorThreads() throws Exception {
        // 出站闸门：暂扣消息使凭据不得先于观察者落定，观察者挂上后再放行，消除内联落定与挂观察者的竞态
        List<ChannelPromise> held = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                io.netty.util.ReferenceCountUtil.release(msg);
                held.add(promise);
            }
        });
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        String prefix = "offload-probe";
        ElasticMicroTaskExecutor executor = ElasticMicroTaskExecutor.of(prefix);
        try {
            AtomicReference<String> headersThread = new AtomicReference<>();
            AtomicReference<String> bodyThread = new AtomicReference<>();
            CompletableFuture<Void> headersObserver = connection
                    .writeHeaders(StatusLine.OK, new LinkedHashMap<>(), executor)
                    .thenRun(() -> headersThread.set(Thread.currentThread().getName()));
            CompletableFuture<Void> bodyObserver = connection
                    .write("body".getBytes(), executor)
                    .thenRun(() -> bodyThread.set(Thread.currentThread().getName()));
            // 观察者已挂上：放行出站，凭据经卸载执行器完成
            for (ChannelPromise promise : held) {
                promise.trySuccess();
            }

            headersObserver.get(5, TimeUnit.SECONDS);
            bodyObserver.get(5, TimeUnit.SECONDS);

            assertTrue(headersThread.get().startsWith(prefix),
                    "首部凭据在卸载执行器线程完成，实际在 " + headersThread.get());
            assertTrue(bodyThread.get().startsWith(prefix),
                    "块写出凭据在卸载执行器线程完成，实际在 " + bodyThread.get());
        } finally {
            executor.close();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void offloadedCredentialFailsWithRejectionWhenExecutorRefuses() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        ElasticMicroTaskExecutor executor = ElasticMicroTaskExecutor.of("offload-probe-reject");
        executor.close();
        try {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> connection.write("body".getBytes(), executor).get(5, TimeUnit.SECONDS));

            assertInstanceOf(RejectedExecutionException.class, failure.getCause(),
                    "执行器未受理映射为凭据以拒绝异常异常完成");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void nullOffloadExecutorKeepsDirectCompletion() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);
        try {
            AtomicReference<Thread> completer = new AtomicReference<>();

            connection.write("body".getBytes(), (MicroTaskExecutor) null)
                    .thenRun(() -> completer.set(Thread.currentThread()))
                    .get(5, TimeUnit.SECONDS);

            assertSame(Thread.currentThread(), completer.get(),
                    "未传执行器时凭据在写出落定线程直通完成");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    // ---------- 序列封章 ----------

    @Test
    void sequencedConnectionSealsEveryContractWrite() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel, 7);

        connection.writeHeaders(StatusLine.OK, new LinkedHashMap<>()).get(5, TimeUnit.SECONDS);
        connection.write("body".getBytes()).get(5, TimeUnit.SECONDS);
        connection.finishWrite().get(5, TimeUnit.SECONDS);

        SequencedDataChunk head = assertInstanceOf(SequencedDataChunk.class, channel.readOutbound(),
                "首部写出以序列号封章");
        assertEquals(7, head.sequence());
        assertInstanceOf(HttpResponse.class, head.message());
        SequencedDataChunk body = assertInstanceOf(SequencedDataChunk.class, channel.readOutbound());
        assertEquals(7, body.sequence());
        ((io.netty.util.ReferenceCounted) body.message()).release();
        SequencedDataChunk last = assertInstanceOf(SequencedDataChunk.class, channel.readOutbound());
        assertEquals(7, last.sequence());
        assertInstanceOf(LastHttpContent.class, last.message());
        channel.finishAndReleaseAll();
    }

    @Test
    void sequencedConnectionSealsTerminalWriteOfClose() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel, 3);

        connection.close();
        channel.runPendingTasks();
        channel.closeFuture().awaitUninterruptibly(5, TimeUnit.SECONDS);

        SequencedDataChunk last = assertInstanceOf(SequencedDataChunk.class, channel.readOutbound(),
                "close 的结束标记同样封章");
        assertEquals(3, last.sequence());
        assertInstanceOf(LastHttpContent.class, last.message());
    }

    @Test
    void unsequencedConnectionWritesPassRaw() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);

        connection.write("body".getBytes()).get(5, TimeUnit.SECONDS);

        // 未携带序列号的连接不参与响应定序，写出保持裸消息
        assertInstanceOf(DefaultHttpContent.class, channel.readOutbound());
        channel.finishAndReleaseAll();
    }
}