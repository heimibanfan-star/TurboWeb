package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 */
public class BackPressFileStream implements FileStream {
    private static final Logger log = LoggerFactory.getLogger(BackPressFileStream.class);
    private final FileChannel fileChannel;
    private final long fileSize;
    private final int chunkSize;
    private long offset;
    DefaultChannelPromise channelFuture;

    public BackPressFileStream(File file, int chunkSize) throws IOException {
        this.fileChannel = FileChannel.open(file.toPath());
        this.fileSize = fileChannel.size();
        this.chunkSize = chunkSize > fileSize ? (int) fileSize : chunkSize;
    }

    @Override
    public ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function) {
        // 判断是否有数据
        if (offset >= fileSize) {
            try {
                if (channelFuture != null) {
                    channelFuture.setSuccess();
                }
                return channelFuture;
            } finally {
                closeFileChannel();
            }
        }
        // 设置读取位置
        try {
            ByteBuf buf = readChunk();
            if (buf != null) {
                ChannelFuture future = function.apply(buf, null);
                if (channelFuture == null) {
                    channelFuture = new DefaultChannelPromise(future.channel());
                }
                // 判断正常回调是否返回了null
                if (future == null) {
                    channelFuture.setFailure(new TurboFileException("文件下载正常回调返回值不能为null"));
                    // 关闭文件通道
                    closeFileChannel();
                    return channelFuture;
                }
                future.addListener(f -> {
                    if (!f.isSuccess()) {
                        channelFuture.setFailure(f.cause());
                        // 关闭文件通道
                        closeFileChannel();
                        log.error("文件传输失败", f.cause());
                    } else {
                        // 读取下一个分块
                        boolean ok = DiskOpeThreadUtils.execute(() -> {
                            readFileWithChunk(function);
                        });
                        if (!ok) {
                            try {
                                if (channelFuture != null) {
                                    channelFuture.setFailure(new TurboFileException("task queue is full, file download error"));
                                }
                                function.apply(null, new TurboFileException("task queue is full, file download error"));
                            } finally {
                                closeFileChannel();
                            }
                        }
                    }
                });
            } else {
                try {
                    if (channelFuture != null) {
                        channelFuture.setSuccess();
                    }
                } finally {
                    closeFileChannel();
                }
            }
            return channelFuture;
        } catch (IOException e) {
            try {
                if (channelFuture != null) {
                    channelFuture.setFailure(e);
                }
                function.apply(null, new TurboFileException("文件读取失败", e));
            } finally {
                closeFileChannel();
            }
        }
        return channelFuture;
    }

    /**
     * 关闭文件通道
     */
    private void closeFileChannel() {
        try {
            fileChannel.close();
        } catch (IOException e) {
            log.error("文件关闭失败", e);
        }
    }

    /**
     * 读取文件的分块
     *
     * @return 文件的分块
     */
    private ByteBuf readChunk() throws IOException {
        fileChannel.position(offset);
        int bufSize = chunkSize > fileSize - offset ? (int) (fileSize - offset) : chunkSize;
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(bufSize);
        // 读取文件内容
        int writeIndex = buf.writerIndex();
        int read = fileChannel.read(buf.nioBuffer(writeIndex, buf.writableBytes()));
        if (read > 0) {
            buf.writerIndex(writeIndex + read);
            offset += read;
            return buf;
        } else {
            return null;
        }
    }
}
