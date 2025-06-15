package top.turboweb.http.response;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.BackupThreadUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.function.BiFunction;

/**
 *
 */
public class AsyncFileStream implements FileStream, Closeable {
    private static final Logger log = LoggerFactory.getLogger(DefaultFileStream.class);
    private final FileChannel fileChannel;
    private final long fileSize;
    private final int chunkSize;
    private long offset;
    private final Object lock = new Object();
    DefaultChannelPromise channelFuture;

    public AsyncFileStream(File file, int chunkSize, boolean backPress) throws IOException {
        this.fileChannel = FileChannel.open(file.toPath());
        this.fileSize = fileChannel.size();
        this.chunkSize = chunkSize > fileSize ? (int) fileSize : chunkSize;
    }

    @Override
    public ChannelFuture readFileWithChunk(BiFunction<ByteBuf, Exception, ChannelFuture> function) {
        // 判断是否有数据
        if (offset >= fileSize) {
            channelFuture.setSuccess();
            return channelFuture;
        }
        // 设置读取位置
        try {
            fileChannel.position(offset);
            int bufSize = chunkSize > fileSize - offset ? (int) (fileSize - offset) : chunkSize;
            ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(bufSize);
            // 读取文件内容
            int read = fileChannel.read(buf.nioBuffer());
            if (read > 0) {
                offset += read;
                ChannelFuture future = function.apply(buf, null);
                if (channelFuture != null) {
                    channelFuture = new DefaultChannelPromise(future.channel());
                }
                future.addListener(f -> {
                    if (!f.isSuccess()) {
                        channelFuture.setFailure(f.cause());
                        log.error("文件读取失败", f.cause());
                    } else {
                        BackupThreadUtils.execute(() -> {
                            readFileWithChunk(function);
                        });
                    }
                });
            }
            return channelFuture;
        } catch (IOException e) {
            channelFuture.setFailure(e);
            function.apply(null, new TurboFileException("文件读取失败", e));
        }
        return channelFuture;
    }
    @Override
    public void close() throws IOException {
        fileChannel.close();
    }
}
