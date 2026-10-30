package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;
import top.turboweb.server.runtime.ChannelAttributeKeys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdleCloseChannelHandler} 的空闲回收与豁免语义锚：
 * 空闲事件抵达时未豁免的连接关闭，附件携带空闲豁免标记的连接不动。
 * 计时器机制属 IdleStateHandler 自身，不在本类用例范围。
 */
class IdleCloseChannelHandlerTest {

    @Test
    void idleEventClosesChannelWithoutExemption() throws Exception {
        IdleCloseChannelHandler handler = new IdleCloseChannelHandler(30);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            ChannelHandlerContext ctx = channel.pipeline().context(handler);

            handler.channelIdle(ctx, IdleStateEvent.ALL_IDLE_STATE_EVENT);

            assertFalse(channel.isOpen(), "空闲超时未豁免的连接必须关闭");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void idleEventSparesExemptedChannel() throws Exception {
        IdleCloseChannelHandler handler = new IdleCloseChannelHandler(30);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.attr(ChannelAttributeKeys.IDLE_EXEMPT).set(Boolean.TRUE);
            ChannelHandlerContext ctx = channel.pipeline().context(handler);

            handler.channelIdle(ctx, IdleStateEvent.ALL_IDLE_STATE_EVENT);

            assertTrue(channel.isOpen(), "豁免连接空闲超时不得关闭");
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
