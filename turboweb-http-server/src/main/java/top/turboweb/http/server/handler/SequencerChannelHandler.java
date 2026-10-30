package top.turboweb.http.server.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import top.turboweb.http.connection.SequencedDataChunk;
import top.turboweb.http.connection.SequencedRequest;

import io.netty.util.concurrent.EventExecutor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.RejectedExecutionException;

/**
 * 定序处理器：同一连接上并发处理的请求，其响应必须按请求到达顺序写出——
 * 前一序列的响应写出终止块之后，后续序列的响应才允许写到管道上（HTTP/1.1 管线化语义）。
 * <p>
 * 入站方向：普通请求经本处理器分配自增序列号，封章为 {@link SequencedRequest} 向下游派发，
 * 下游以序列号建立携带该号的连接，其响应方可凭号定序；升级协议握手等携带Upgrade头的
 * 请求不经定序，原样透传不占序列号。在途请求数（请求游标与写出游标的差值）在派发时裁决，
 * 超过上限视为客户端异常积压：断开连接，缓冲中的写出凭据以失败收场，本请求不再派发。
 * <p>
 * 出站方向：序列号与写出游标一致的响应分块即时透传；提前写出的响应整体缓冲——
 * 同一序列的分块以链表按写出顺序串接，链表所属响应以序列号为序入堆。
 * 某响应写出终止块后写出游标进一，随后逐个冲刷堆顶恰为下一序列的缓冲链表：
 * 链表不含终止块时只冲刷已缓冲的分块，其后到达的分块因游标已对齐继续直写。
 * 断连或连接失活时缓冲消息全部释放，缓冲中的写出凭据以失败收场。
 * 不携带序列号的出站消息（如升级协议握手响应）不经定序，原样透传。
 * <p>
 * 处理器持有连接级状态（游标、堆与缓冲），不能跨channel复用，
 * 由 {@link top.turboweb.http.server.runtime.HttpServerRuntime} 在装配每条管道时新建独立实例。
 * 本处理器不持锁：读入与连接失活由 Netty 管线分发保证在本channel的eventLoop线程调用，
 * 状态直接读写；写出可来自任意线程（虚拟线程直接写出），落在eventLoop线程时直接执行定序裁决，
 * 否则按 Netty 默认语义提交eventLoop执行。游标、缓冲与关闭标记仅由eventLoop线程访问。
 */
public class SequencerChannelHandler extends ChannelDuplexHandler {

    /** 请求游标：调度期为每个请求分配的自增序列号，仅eventLoop线程访问 */
    private long sequenceAllocator;

    /** 在途响应数上限：请求游标与写出游标的差值超过即断连 */
    private final int maxInFlightResponses;

    /** 写出游标：当前允许直写的序列号，从首个序列号开始 */
    private long writeCursor = 1;

    /** 缓冲堆：按序列号排序的缓冲响应 */
    private final PriorityQueue<PendingResponse> pendingHeap = new PriorityQueue<>();

    /** 缓冲索引：序列号到缓冲响应的映射，供同序列分块追加时定位 */
    private final Map<Long, PendingResponse> pendingIndex = new HashMap<>();

    /** 断连或失活后置位，其后到达的序列化写出直接失败 */
    private boolean closed = false;

    /**
     * 构建响应定序处理器。
     *
     * @param maxInFlightResponses 在途响应数上限，须为正数
     */
    public SequencerChannelHandler(int maxInFlightResponses) {
        if (maxInFlightResponses < 1) {
            throw new IllegalArgumentException("maxInFlightResponses must be greater than 0");
        }
        this.maxInFlightResponses = maxInFlightResponses;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 升级协议握手等非普通请求不经定序，不占序列号，原样透传
        if (!(msg instanceof FullHttpRequest request) || request.headers().contains(HttpHeaderNames.UPGRADE)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (closed) {
            // 断连后残余流入的请求直接释放，不再派发
            request.release();
            return;
        }
        long sequence = ++sequenceAllocator;
        if (sequence < 0) {
            // 序列号分配器越过Long上界回绕为负，序列比较与游标差值语义失效：断开连接回收
            request.release();
            closeAndFail(ctx);
            return;
        }
        if (sequence - (writeCursor - 1) > maxInFlightResponses) {
            // 在途请求数（请求游标与写出游标的差值）超过上限：
            // 客户端异常积压，断开连接，本请求不再派发
            request.release();
            closeAndFail(ctx);
            return;
        }
        // 入站封章：携带分配的序列号向下游派发
        ctx.fireChannelRead(new SequencedRequest(sequence, request));
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof SequencedDataChunk data)) {
            // 升级协议握手等非序列化出站消息不经定序，原样透传
            ctx.write(msg, promise);
            return;
        }
        EventExecutor executor = ctx.executor();
        if (executor.inEventLoop()) {
            writeSequenced(ctx, data, promise);
        } else {
            // 按Netty默认语义提交eventLoop执行：提交被拒时释放消息并以失败收场凭据
            try {
                executor.execute(() -> writeSequenced(ctx, data, promise));
            } catch (RejectedExecutionException e) {
                ReferenceCountUtil.release(data.message());
                promise.tryFailure(e);
            }
        }
    }

    /**
     * 序列化写出的定序裁决：仅由eventLoop线程执行，游标与缓冲无并发访问。
     */
    private void writeSequenced(ChannelHandlerContext ctx, SequencedDataChunk data, ChannelPromise promise) {
        if (closed) {
            ReferenceCountUtil.release(data.message());
            promise.tryFailure(new IllegalStateException("connection is closed"));
            return;
        }
        Object message = data.message();
        if (data.sequence() == writeCursor) {
            // 轮到本序列写出：直写管道；终止块写出后游标进一并冲刷堆顶
            ctx.write(message, promise);
            if (message instanceof LastHttpContent) {
                writeCursor++;
                drain(ctx);
            }
        } else {
            buffer(data, promise);
        }
    }

    /**
     * 缓冲提前写出的消息：同序列分块以链表按写出顺序串接，响应整体以序列号为序入堆。
     */
    private void buffer(SequencedDataChunk data, ChannelPromise promise) {
        PendingResponse pending = pendingIndex.get(data.sequence());
        if (pending == null) {
            pending = new PendingResponse(data.sequence());
            pendingIndex.put(data.sequence(), pending);
            pendingHeap.add(pending);
        }
        if (data.message() instanceof LastHttpContent) {
            pending.terminal = true;
        }
        pending.writes.add(new BufferedWrite(data.message(), promise));
    }

    /**
     * 冲刷堆顶恰为写出游标的缓冲链表：不含终止块的响应只冲刷已缓冲分块后移出堆，
     * 其后到达的分块因游标已对齐继续直写；含终止块的响应整体冲刷后游标再进一，
     * 循环至堆顶序列断档为止。仅由eventLoop线程调用。
     */
    private void drain(ChannelHandlerContext ctx) {
        while (!pendingHeap.isEmpty() && pendingHeap.peek().sequence == writeCursor) {
            PendingResponse pending = pendingHeap.poll();
            pendingIndex.remove(pending.sequence);
            for (BufferedWrite buffered : pending.writes) {
                ctx.write(buffered.message(), buffered.promise());
            }
            if (pending.terminal) {
                writeCursor++;
            }
        }
    }

    /**
     * 断开连接并清空缓冲：缓冲消息全部释放，缓冲中的写出凭据以失败收场；
     * 其后到达的序列化写出直接失败。
     */
    private void closeAndFail(ChannelHandlerContext ctx) {
        failAndReleaseBuffered();
        closed = true;
        ctx.channel().close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        failAndReleaseBuffered();
        closed = true;
        ctx.fireChannelInactive();
    }

    /**
     * 清空缓冲：缓冲消息按引用计数口径释放，缓冲中的写出凭据以失败收场。
     */
    private void failAndReleaseBuffered() {
        List<PendingResponse> drained = new ArrayList<>(pendingHeap.size());
        PendingResponse pending;
        while ((pending = pendingHeap.poll()) != null) {
            drained.add(pending);
        }
        pendingIndex.clear();
        for (PendingResponse response : drained) {
            for (BufferedWrite buffered : response.writes) {
                ReferenceCountUtil.release(buffered.message());
                buffered.promise().tryFailure(new IllegalStateException("connection is closed"));
            }
        }
    }

    /** 单条缓冲的写出：原始出站消息与随行的写出凭据 */
    private record BufferedWrite(Object message, ChannelPromise promise) {
    }

    /** 单个缓冲响应：同序列分块的链表与是否已含终止块的标记 */
    private static final class PendingResponse implements Comparable<PendingResponse> {

        private final long sequence;
        private final Deque<BufferedWrite> writes = new ArrayDeque<>();
        private boolean terminal;

        private PendingResponse(long sequence) {
            this.sequence = sequence;
        }

        @Override
        public int compareTo(PendingResponse other) {
            return Long.compare(sequence, other.sequence);
        }
    }
}
