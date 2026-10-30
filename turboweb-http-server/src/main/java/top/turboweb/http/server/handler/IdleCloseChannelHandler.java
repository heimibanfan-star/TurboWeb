package top.turboweb.http.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import top.turboweb.server.runtime.ChannelAttributeKeys;

import java.util.concurrent.TimeUnit;

/**
 * 连接空闲回收处理器：读写皆无持续超过阈值视为死连接，关闭以回收其内存——
 * 定序缓冲与出站缓冲中的消息随连接失活统一释放，缓冲中的写出凭据以失败收场。
 * <p>
 * 只监测全部空闲（读与写皆无）：仅有读无写的慢上传连接不回收，写出的判定
 * 由IdleStateHandler按穿过本处理器的出站消息记录，定序处理器内部缓冲中
 * 尚未放行的写出不计入活动。通道附件存在 {@link ChannelAttributeKeys#IDLE_EXEMPT}
 * 标记的长生命周期连接（websocket、SSE等）不回收，空闲事件被吞不向下游传递。
 * <p>
 * 处理器持有连接级计时状态，不能跨channel复用，每条管道新建独立实例。
 */
public class IdleCloseChannelHandler extends IdleStateHandler {

    /**
     * 构建连接空闲回收处理器。
     *
     * @param allIdleTimeMillis 读写皆无的空闲阈值，单位ms
     */
    public IdleCloseChannelHandler(long allIdleTimeMillis) {
        super(0, 0, allIdleTimeMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (!ctx.channel().hasAttr(ChannelAttributeKeys.IDLE_EXEMPT)) {
            ctx.close();
        }
    }
}
