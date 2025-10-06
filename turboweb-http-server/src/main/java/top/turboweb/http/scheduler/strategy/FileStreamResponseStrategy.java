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
 * 处理文件流响应的策略
 */
public class FileStreamResponseStrategy extends ResponseStrategy {
    private static final Logger log = LoggerFactory.getLogger(FileStreamResponseStrategy.class);

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
     * 带背压的分块发送文件
     *
     * @param response 响应
     * @param session  会话
     * @param promise  结果
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
     * 读取文件分块
     *
     * @param position 文件的偏移量
     * @param sink     背压流
     * @param response 文件的响应
     * @return 文件的偏移量
     * @throws IOException 读取文件时发生异常
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
     * 关闭文件通道
     *
     * @param response 文件响应
     */
    private void closeFileChannel(FileStreamResponse response) {
        try {
            response.getFileChannel().close();
        } catch (IOException e) {
            log.error("关闭文件通道时出现错误", e);
        }
    }

}
