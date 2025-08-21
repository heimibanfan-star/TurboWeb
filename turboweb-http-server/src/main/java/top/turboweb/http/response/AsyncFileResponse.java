package top.turboweb.http.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboFileException;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.commons.utils.thread.WorkStealThreadUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * 基于AIO实现的文件响应对象
 */
public class AsyncFileResponse extends AbstractFileResponse implements InternalCallResponse, Closeable {

    private final AsynchronousFileChannel asynchronousFileChannel;
    private static final Set<? extends OpenOption> options = Set.of(StandardOpenOption.READ);
    private final ByteBuffer buffer;
    private final long fileSize;
    private long position = 0;

    public AsyncFileResponse(File file) {
        this(file, 8192);
    }

    public AsyncFileResponse(File file, int chunkSize) {
        this(HttpResponseStatus.OK, file, chunkSize, GlobalConfig.getResponseCharset());
    }

    public AsyncFileResponse(HttpResponseStatus status, File file, int chunkSize, Charset filenameCharset) {
        super(status, file, filenameCharset);
        try {
            asynchronousFileChannel = AsynchronousFileChannel.open(
                    file.toPath(),
                    options,
                    WorkStealThreadUtils.getExecutorService()
            );
            fileSize = asynchronousFileChannel.size();
        } catch (IOException e) {
            throw new TurboFileException(e);
        }
        // 设置响应头
        this.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileSize);
        this.buffer = ByteBuffer.allocate(chunkSize);
    }

    /**
     * 获取AIO文件读取通道
     *
     * @return 文件通道
     */
    public AsynchronousFileChannel getAsynchronousFileChannel() {
        return asynchronousFileChannel;
    }

    /**
     * 获取当前已读取的位置
     *
     * @return 已读取的位置
     */
    public long getPosition() {
        return position;
    }

    /**
     * 设置当前已读取的位置
     *
     * @param position 已读取的位置
     */
    public void setPosition(long position) {
        this.position = position;
    }

    /**
     * 获取当前文件剩余未读取的字节数
     *
     * @return 文件剩余未读取的字节数
     */
    public long getRemaining() {
        return fileSize - position;
    }

    /**
     * 获取分块缓冲区
     *
     * @return 分块缓冲区
     */
    public ByteBuffer chunkBuffer() {
        return buffer;
    }

    @Override
    public InternalCallType getType() {
        return InternalCallType.AIO_FILE;
    }

    @Override
    public void close() throws IOException {
        asynchronousFileChannel.close();
    }
}
