package top.turboweb.client.result;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.exception.TurboHttpClientException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 默认的 Stream 实现
 *
 * <p>
 * 特点：
 * <ul>
 *     <li>基于 BlockingQueue 实现生产者-消费者模型</li>
 *     <li>支持 Byte / byte[] / String 三种读取方式</li>
 *     <li>字符串读取支持跨 ByteBuf 的 CharsetDecoder 解码</li>
 *     <li>支持异常透传</li>
 * </ul>
 *
 * <strong>线程模型：</strong>
 * <ul>
 *     <li>生产者：Netty IO 线程向 contentQueue 写入 ByteBuf</li>
 *     <li>消费者：用户线程调用 nextXXX 方法阻塞读取</li>
 * </ul>
 */
public class DefaultStream implements InternStream {

    /**
     * 表示流结束状态的内部标记
     * error != null 表示异常结束
     */
    private record Finish(Throwable error) {
    }

    /** 当前流是否已经完成（volatile 确保可见性） */
    private volatile Finish completed = null;

    /**
     * 当前读取的数据类型
     *
     * <p>
     * 用于防止同一个 Stream 实例：
     * 既按字节读取，又按字符串读取，导致数据错乱
     * </p>
     */
    private Class<?> readType = null;

    /**
     * 存放响应内容的队列
     *
     * <p>
     * 由 Netty IO 线程写入，
     * 用户线程阻塞读取
     * </p>
     */
    private final BlockingQueue<ByteBuf> contentQueue;

    /** HTTP 响应头 */
    private final HttpHeaders headers;

    /** HTTP 响应状态 */
    private final HttpResponseStatus status;

    /** Trailer Headers（通常用于 chunked 编码） */
    private final Future<HttpHeaders> trailerHeaders;

    /** 字符串解码器（延迟初始化） */
    private CharsetDecoder decoder;

    /**
     * 字节输入缓冲区
     *
     * <p>
     * 用于处理跨 ByteBuf 的字符解码
     * </p>
     */
    private ByteBuffer inBuffer = ByteBuffer.allocate(8192);

    /** 字符输出缓冲区 */
    private final CharBuffer outBuffer = CharBuffer.allocate(8192);

    /** 是否已经到达输入流末尾 */
    private boolean endOfInput = false;




    public DefaultStream(
            BlockingQueue<ByteBuf> contentQueue,
            HttpHeaders headers,
            HttpResponseStatus status,
            Future<HttpHeaders> trailerHeaders
    ) {
        Objects.requireNonNull(contentQueue);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(status);
        Objects.requireNonNull(trailerHeaders);
        this.contentQueue = contentQueue;
        this.headers = headers;
        this.status = status;
        this.trailerHeaders = trailerHeaders;
    }

    @Override
    public HttpResponseStatus status() {
        return this.status;
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public ByteBuf nextContent() {
        return this.nextContent(3000);
    }

    /**
     * 从队列中读取 ByteBuf
     *
     * <p>
     * 行为说明：
     * <ul>
     *     <li>优先读取已有数据</li>
     *     <li>若无数据则阻塞等待 allowLazyTime(意外情况，概率极低)</li>
     *     <li>若流已结束且无剩余数据，返回 null 或抛异常</li>
     * </ul>
     * </p>
     */
    private ByteBuf doRead(long allowLazyTime) {
        // 轮询队列，等待数据或完成信号
        for (; ; ) {
            // 若流已完成且队列中已无剩余数据，直接结束
            if (completed != null && this.contentQueue.isEmpty()) {
                return handleForEnd();
            }
            try {
                ByteBuf byteBuf = this.contentQueue.poll(allowLazyTime, TimeUnit.MILLISECONDS);
                if (byteBuf != null) {
                    return byteBuf;
                }
            } catch (InterruptedException ignore) {
            }
            // 若在等待期间检测到流已完成，则结束读取
            if (this.completed != null) {
                return handleForEnd();
            }
        }

    }

    private void checkByByte() {
        // 判断当前类型是否符合当前方法
        if (readType == null) {
            readType = Byte.class;
        } else if (readType != Byte.class) {
            throw new TurboHttpClientException("this stream has been read by other category method");
        }
    }

    private void checkByString() {
        // 判断当前类型是否符合当前方法
        if (readType == null) {
            readType = String.class;
        } else if (readType != String.class) {
            throw new TurboHttpClientException("this stream has been read by other category method");
        }
    }

    @Override
    public ByteBuf nextContent(long allowLazyTime) {
        checkByByte();
        // 读取数据
        return doRead(allowLazyTime);
    }

    @Override
    public byte[] nextBytes()  {
        return this.nextBytes(3000);
    }

    @Override
    public byte[] nextBytes(long allowLazyTime)  {
        checkByByte();
        ByteBuf buf = doRead(allowLazyTime);
        try {
            // 返回空的数据，则返回 null
            if (buf == null) {
                return null;
            }
            // 读取数据
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            // 释放资源
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * 流式读取字符串
     *
     * <p>
     * 特点：
     * <ul>
     *     <li>使用 CharsetDecoder 支持多字节字符</li>
     *     <li>支持字符跨 ByteBuf 边界</li>
     *     <li>一旦有字符产出立即返回，保证流式体验</li>
     * </ul>
     * </p>
     */
    @Override
    public String nextString(Charset charset, long allowLazyTime)  {
        Objects.requireNonNull(charset, "charset can not be null");
        checkByString();
        // 如果解码器不存在则创建解码器
        if (decoder == null) {
            decoder = charset.newDecoder();
        }
        // 用于接收结果
        StringBuilder result = new StringBuilder();
        for (; ; ) {
            // 尝试解码已有数据
            inBuffer.flip();
            CoderResult cr = decoder.decode(inBuffer, outBuffer, endOfInput);
            inBuffer.compact();
            outBuffer.flip();
            if (outBuffer.hasRemaining()) {
                result.append(outBuffer);
            }
            // 清空缓冲区
            outBuffer.clear();
            // 有字符产出，立刻返回（流式）
            if (!result.isEmpty()) {
                return result.toString();
            }
            // 处理解码结果
            if (cr.isError()) {
                try {
                    cr.throwException();
                } catch (CharacterCodingException e) {
                    throw new TurboHttpClientException(e);
                }
            }
            //  如果已经结束输入且没有更多字符
            if (endOfInput) {
                return null;
            }
            //  拉取新的 ByteBuf
            ByteBuf buf = doRead(allowLazyTime);
            if (buf == null) {
                endOfInput = true;
                continue;
            }
            try {
                // 确保 inBuffer 容量足够
                if (buf.readableBytes() > inBuffer.remaining()) {
                    int newCap = Math.max(
                            inBuffer.capacity() * 2,
                            inBuffer.position() + buf.readableBytes()
                    );
                    ByteBuffer newBuf = ByteBuffer.allocate(newCap);
                    inBuffer.flip();
                    newBuf.put(inBuffer);
                    inBuffer = newBuf;
                }
                inBuffer.put(buf.nioBuffer(buf.readerIndex(), buf.readableBytes()));
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public String nextString(Charset charset) {
        return this.nextString(charset, 3000);
    }

    @Override
    public String nextString(long allowLazyTime) {
        return this.nextString(StandardCharsets.UTF_8, allowLazyTime);
    }

    @Override
    public String nextString() {
        return this.nextString(StandardCharsets.UTF_8, 3000);
    }


    /**
     * 获取CharBuffer的容量
     *
     * @param in ByteBuffer
     * @return CharBuffer的容量
     */
    private int getCharBufferSize(ByteBuffer in) {
        return (int) (in.remaining() * decoder.maxCharsPerByte());
    }


    private ByteBuf handleForEnd() {
        if (completed.error() != null) {
            Throwable throwable = completed.error();
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TurboHttpClientException(throwable);
        }
        return null;
    }

    @Override
    public Future<HttpHeaders> trailerHeaders() {
        return this.trailerHeaders;
    }


    @Override
    public void completed(Throwable error) {
        this.completed = new Finish(error);
    }
}
