package top.turboweb.http.server.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 背压节流处理器：把出站消息写往对端的时机与通道水位线对齐。
 * <p>
 * 通道不可写期间，经过本处理器的出站消息与冲刷事件暂扣挂起，可写恢复时按到达次序放行并统一冲刷，
 * 使健康链路上的写出不损失延迟、停滞链路上的写出积攒合批。
 * 暂扣以 FIFO 保持到达次序，对携带序列号与不携带序列号的消息、websocket 帧等
 * 全部出站消息同口径生效；不可写期间的冲刷事件不放行，随可写恢复后的统一冲刷一并兑现。
 * </p>
 * <p>
 * 暂扣缓冲的上界由消费方链式等待写出凭据的口径保证：凭据在暂扣期间不完成，
 * 链式等待的生产者随即停住；不等待凭据的连续写出在通道持续不可写期间会在本处理器内积压。
 * 连接断开时暂扣消息按引用计数口径释放，凭据以失败收场。
 * </p>
 * <p>
 * 本处理器持有连接级暂扣状态，须每条连接新建；全部状态仅在eventLoop线程访问
 * （出站事件的处理器调用由 Netty 保证在eventLoop线程执行）。
 * </p>
 */
public class WriteThrottleChannelHandler extends ChannelDuplexHandler {

    /** 不可写期间暂扣的写出：原始出站消息与随行的写出凭据 */
    private final Deque<ThrottledWrite> heldWrites = new ArrayDeque<>();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (ctx.channel().isWritable()) {
            ctx.write(msg, promise);
        } else {
            heldWrites.add(new ThrottledWrite(msg, promise));
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            ctx.flush();
        }
        // 不可写期间不放行冲刷：可写恢复时的统一冲刷会一并把积攒的数据冲出，无需显式记挂起状态
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            drain(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            failAndReleaseHeld();
        } finally {
            ctx.fireChannelInactive();
        }
    }

    /**
     * 放行暂扣的写出并兑现挂起冲刷：暂扣以 FIFO 放行保持到达次序，
     * 仅由eventLoop线程执行，队列与挂起标记无并发访问。
     */
    private void drain(ChannelHandlerContext ctx) {
        ThrottledWrite throttled;
        while ((throttled = heldWrites.poll()) != null) {
            ctx.write(throttled.message(), throttled.promise());
        }
        ctx.flush();
    }

    /**
     * 清空暂扣：消息按引用计数口径释放，凭据以失败收场。
     */
    private void failAndReleaseHeld() {
        ThrottledWrite throttled;
        while ((throttled = heldWrites.poll()) != null) {
            ReferenceCountUtil.release(throttled.message());
            throttled.promise().tryFailure(new IllegalStateException("connection is closed"));
        }
    }

    /** 单条暂扣的写出：原始出站消息与随行的写出凭据 */
    private record ThrottledWrite(Object message, ChannelPromise promise) {
    }
}
