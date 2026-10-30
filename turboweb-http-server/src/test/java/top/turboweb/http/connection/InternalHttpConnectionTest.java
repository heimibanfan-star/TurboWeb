package top.turboweb.http.connection;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.StatusLine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
    void finishWriteEmitsTerminalLastContent() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        InternalHttpConnection connection = new InternalHttpConnection(channel);

        connection.finishWrite().get(5, TimeUnit.SECONDS);

        assertInstanceOf(LastHttpContent.class, channel.readOutbound(), "结束标记以终止块写出");
        channel.finishAndReleaseAll();
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