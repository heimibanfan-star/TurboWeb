package top.turboweb.http.orchestrator.writer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.exception.ResponseHandleException;
import top.turboweb.http.connection.InternalHttpConnection;
import top.turboweb.http.response.FileStreamResponse;

import java.io.IOException;

/**
 * 文件流响应写出器。
 * <p>
 * 当响应类型为 {@link FileStreamResponse} 时，由该写出器接管响应的发送过程。
 * 该写出器基于 Reactor 背压流与 Netty 的异步通道，实现了真正的非阻塞文件分块传输。
 * </p>
 *
 * <p><b>磁盘IO执行器来源：</b></p>
 * 磁盘IO微任务执行器经 {@link #initWriter} 在装配与登记期注入（继承自基类字段），
 * 用于调度文件分块的读取任务，本写出器不负责其生命周期。
 *
 * <p><b>背压机制：</b></p>
 * <ul>
 *     <li>{@link Flux#generate} 按偏移量递增产出分块，读取与写出交替推进；</li>
 *     <li>每个分块的写出成功后才请求下一块，执行器未受理（未受理收尾）即取消整个流；</li>
 *     <li>流终结（完成、出错、取消）时统一关闭文件通道。</li>
 * </ul>
 */
public class FileStreamResponseWriter extends AbstractResponseWriter {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(FileStreamResponseWriter.class);

    /**
     * 处理 {@link FileStreamResponse} 类型的文件流响应。
     * <p>
     * 先写出响应头，头写成功后启动带背压控制的分块发送，
     * 返回代表整个传输完成状态的 promise。
     *
     * @param response 响应对象
     * @param session  内部连接会话
     * @return 表示发送结果的 {@link ChannelFuture}
     */
    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalHttpConnection session) {
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
    private void doSendFileWithBackPress(FileStreamResponse response, InternalHttpConnection session, ChannelPromise promise) {
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
                    protected void hookOnSubscribe(@NonNull Subscription subscription) {
                        diskIoExecutor.submit(() -> {
                            // 订阅一个流
                            subscription.request(1);
                        }, signal -> {
                            if (signal.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT) {
                                log.error("disk thread is busy, file download fail");
                                cancel();
                            }
                        });
                    }

                    @Override
                    protected void hookOnNext(@NonNull ByteBuf value) {
                        // 将数据发送出去
                        session.getChannel().writeAndFlush(new DefaultHttpContent(value))
                                .addListener(f -> {
                                    if (!f.isSuccess()) {
                                        log.error("chunk send error", f.cause());
                                        cancel();
                                    } else {
                                        // 订阅下一个分块
                                        diskIoExecutor.submit(() -> request(1), signal -> {
                                            if (signal.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT) {
                                                log.error("disk thread is busy, file download fail");
                                                cancel();
                                            }
                                        });
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
                    protected void hookOnError(@NonNull Throwable throwable) {
                        promise.setFailure(throwable);
                        // 关闭连接管道
                        session.close();
                    }

                    @Override
                    protected void hookOnCancel() {
                        promise.setFailure(new ResponseHandleException("file download is cancel"));
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
        // 按流逻辑偏移做绝对读：读取起点与窗口坐标一致，offset>0 时不会错位读物理文件头
        int read = response.getFileChannel().read(buf.nioBuffer(writeIndex, bufSize), position);
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
            log.error("Error closing file channel", e);
        }
    }

}
