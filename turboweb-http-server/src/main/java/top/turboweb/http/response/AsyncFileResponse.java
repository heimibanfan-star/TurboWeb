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
 * 基于异步 I/O (AIO) 的文件响应对象。
 * <p>
 * 该类通过 {@link AsynchronousFileChannel} 实现文件的异步分块读取，
 * 可用于高性能 HTTP 文件传输场景，避免阻塞线程。
 * </p>
 * <p>
 * 继承自 {@link AbstractFileResponse} 并实现 {@link InternalCallResponse} 和 {@link Closeable}。
 * </p>
 */
public class AsyncFileResponse extends AbstractFileResponse implements InternalCallResponse, Closeable {

    /** 异步文件通道，用于 AIO 文件读取 */
    private final AsynchronousFileChannel asynchronousFileChannel;
    /** 打开文件通道的选项 */
    private static final Set<? extends OpenOption> options = Set.of(StandardOpenOption.READ);
    /** 分块缓冲区 */
    private final ByteBuffer buffer;
    /** 文件总大小 */
    private final long fileSize;
    /** 当前读取位置 */
    private long position = 0;

    /**
     * 构造方法，使用默认分块大小 8192 字节
     *
     * @param file 文件对象
     */
    public AsyncFileResponse(File file) {
        this(file, 8192);
    }

    /**
     * 构造方法，可指定分块大小
     *
     * @param file      文件对象
     * @param chunkSize 分块缓冲区大小
     */
    public AsyncFileResponse(File file, int chunkSize) {
        this(HttpResponseStatus.OK, file, chunkSize, GlobalConfig.getResponseCharset());
    }

    /**
     * 构造方法，可指定 HTTP 状态、文件、分块大小及文件名编码
     *
     * @param status         HTTP 响应状态
     * @param file           文件对象
     * @param chunkSize      分块缓冲区大小
     * @param filenameCharset 文件名编码字符集
     */
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
     * 获取异步文件通道
     *
     * @return {@link AsynchronousFileChannel} 对象
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
     * @return {@link ByteBuffer} 分块缓冲区
     */
    public ByteBuffer chunkBuffer() {
        return buffer;
    }

    /**
     * {@inheritDoc}
     * @return 返回内部调用类型 {@link InternalCallType#AIO_FILE}
     */
    @Override
    public InternalCallType getType() {
        return InternalCallType.AIO_FILE;
    }

    /**
     * 关闭异步文件通道
     *
     * @throws IOException 文件通道关闭异常
     */
    @Override
    public void close() throws IOException {
        asynchronousFileChannel.close();
    }
}
