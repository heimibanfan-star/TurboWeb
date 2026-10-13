package top.turboweb.http.orchestrator;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.InternalConnectSession;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link IgnoredResponseWriter} 的行为锚：空操作，不写出任何内容、无写出凭据。
 */
class IgnoredResponseWriterTest {

    @Test
    void returnsNullFutureAndWritesNothing() {
        EmbeddedChannel channel = new EmbeddedChannel();
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

        assertNull(new IgnoredResponseWriter()
                .write(response, new InternalConnectSession(channel)),
                "忽略写出器无写出凭据");
        assertNull(channel.readOutbound(), "忽略写出器不得写出任何响应");
        response.release();
        channel.finishAndReleaseAll();
    }
}
