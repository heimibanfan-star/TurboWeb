package top.turboweb.http.scheduler.strategy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.http.connect.InternalConnectSession;
import top.turboweb.http.response.FileStreamResponse;

import java.io.IOException;

/**
 * 文件流响应处理策略。
 * <p>
 * 当 {@link ResponseStrategy} 检测到响应类型为 {@link FileStreamResponse} 时，
 * 由该策略接管响应的发送过程。该策略基于 Reactor 背压流与 Netty 的异步通道，
 * 实现了真正的非阻塞文件分块传输。
 * </p>
 *
 * <p>
 * 设计目标：
 * <ul>
 *   <li>在文件下载过程中避免阻塞 Netty 的 I/O 线程。</li>
 *   <li>通过 Reactor 的 {@code Flux.generate()} 实现分块读取与背压控制。</li>
 *   <li>利用 {@link DiskOpeThreadUtils} 调度磁盘 I/O 线程，实现业务线程与网络线程的分离。</li>
 *   <li>保证文件通道在传输完成或异常时被安全关闭。</li>
 * </ul>
 * </p>
 */
public class FileStreamResponseStrategy extends ResponseStrategy {
    private static final Logger log = LoggerFactory.getLogger(FileStreamResponseStrategy.class);

    /**
     * 处理 {@link FileStreamResponse} 类型的文件流响应。
     * <p>
     * 如果响应类型不匹配，则抛出 {@link IllegalArgumentException}。
     * </p>
     *
     * @param response HTTP 响应对象
     * @param session  内部连接会话
     * @return 表示发送结果的 {@link ChannelFuture}
     */
    @Override
    protected ChannelFuture doHandle(HttpResponse response, InternalConnectSession session) {
        if (response instanceof FileStreamResponse fileStreamResponse) {
            ChannelPromise promise = session.getChannel().newPromise();
            // 发送响应头
            session.getChannel().writeAndFlush(fileStreamResponse)
                    .addListener(f -> {
                        if (!f.isSuccess()) {
                            promise.setFailure(f.cause());
                        } else {
                            doSendFileWithBackPress(fileStreamResponse, session, promise);
                        }
                    });
            return promise;
        } else {
            throw new IllegalArgumentException("Invalid response type:" + response.getClass().getName());
        }
    }

    /**
     * 启动带背压控制的文件分块发送。
     * <p>
     * 使用 {@link Flux#generate} 创建按偏移量递增的分块读取流，
     * 结合 {@link BaseSubscriber} 的订阅与请求节奏，实现动态背压调度。
     * </p>
     *
     * @param response 文件响应对象
     * @param session  当前会话
     * @param promise  异步结果回调
     */
    private void doSendFileWithBackPress(FileStreamResponse response, InternalConnectSession session, ChannelPromise promise) {
        // 创建Reactor流进行文件的背压发送
        Flux.<ByteBuf, Long>generate(response::getOffset, (offset, emitter) -> {
                    try {
                        Long pos = doReadChunk(offset, emitter, response);
                        if (pos == -1L) {
                            emitter.complete();
                        }
                        return pos;
                    } catch (IOException e) {
                        emitter.error(e);
                        return offset;
                    }
                })
                // 处理资源的释放问题
                .doFinally(signalType -> {
                    closeFileChannel(response);
                })
                .subscribe(new BaseSubscriber<>() {

                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        boolean ok = DiskOpeThreadUtils.execute(() -> {
                            // 订阅一个流
                            subscription.request(1);
                        });
                        if (!ok) {
                            log.error("disk thread is busy, file download fail");
                            cancel();
                        }
                    }

                    @Override
                    protected void hookOnNext(ByteBuf value) {
                        // 将数据发送出去
                        session.getChannel().writeAndFlush(new DefaultHttpContent(value))
                                .addListener(f -> {
                                    if (!f.isSuccess()) {
                                        log.error("chunk send error", f.cause());
                                        cancel();
                                    } else {
                                        // 订阅下一个分块
                                        boolean ok = DiskOpeThreadUtils.execute(() -> {
                                            request(1);
                                        });
                                        if (!ok) {
                                            log.error("disk thread is busy, file download fail");
                                            cancel();
                                        }
                                    }
                                });
                    }

                    @Override
                    protected void hookOnComplete() {
                        // 发送结束标识
                        session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                                .addListener(f -> {
                                    if (!f.isSuccess()) {
                                        log.error("chunk send error", f.cause());
                                    } else {
                                        promise.setSuccess();
                                    }
                                });
                    }

                    @Override
                    protected void hookOnError(Throwable throwable) {
                        promise.setFailure(throwable);
                        // 关闭连接管道
                        session.close();
                    }

                    @Override
                    protected void hookOnCancel() {
                        promise.setFailure(new TurboFileException("file download is cancel"));
                    }
                });
    }

    /**
     * 从文件中读取一个分块的数据。
     * <p>
     * 使用 {@link PooledByteBufAllocator#DEFAULT} 分配直接内存，
     * 以提高文件传输性能并减少堆内存复制。
     * </p>
     *
     * @param position 当前文件读取偏移量
     * @param sink     Reactor 同步流发射器
     * @param response 文件响应
     * @return 下一次读取的偏移量；返回 {@code -1} 表示文件读取完毕
     * @throws IOException 文件读取失败时抛出
     */
    private Long doReadChunk(Long position, SynchronousSink<ByteBuf> sink, FileStreamResponse response) throws IOException {
        long remaining = response.getEnd() - position;
        if (remaining <= 0) {
            return -1L;
        }
        // 创建bytebuf
        int bufSize = (int) Math.min(remaining, response.getChunkSize());
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(bufSize);
        int writeIndex = buf.writerIndex();
        int read = response.getFileChannel().read(buf.nioBuffer(writeIndex, bufSize));
        if (read > 0) {
            buf.writerIndex(writeIndex + read);
            sink.next(buf);
            return position + read;
        } else {
            buf.release();
            return -1L;
        }
    }

    /**
     * 安全关闭文件通道。
     * <p>
     * 无论正常结束还是异常退出，均应保证文件通道释放，
     * 避免资源泄漏。
     * </p>
     *
     * @param response 文件响应对象
     */
    private void closeFileChannel(FileStreamResponse response) {
        try {
            response.getFileChannel().close();
        } catch (IOException e) {
            log.error("关闭文件通道时出现错误", e);
        }
    }

}
