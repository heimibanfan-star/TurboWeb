package top.turboweb.http.response;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.config.GlobalConfig;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * 基于零拷贝（Zero-Copy）技术实现的文件响应对象。
 * <p>
 * 使用 Netty 的 {@link FileRegion} 直接将文件内容传输到网络通道，
 * 避免了文件内容在用户空间和内核空间之间的拷贝，从而提高文件传输性能。
 * <p>
 * 适用于大文件传输或高吞吐量场景。
 */
public class ZeroCopyResponse extends AbstractFileResponse implements InternalCallResponse {

    /**
     * 文件传输区域对象，用于零拷贝传输。
     */
    private final FileRegion fileRegion;

    /**
     * 使用文件及指定偏移量和长度创建零拷贝响应对象。
     *
     * @param file   待传输的文件
     * @param offset 文件传输起始偏移量
     * @param length 文件传输长度
     */
    public ZeroCopyResponse(File file, long offset, long length) {
        super(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset());
        fileRegion = new DefaultFileRegion(file, offset, length);
        this.headers().add(HttpHeaderNames.CONTENT_LENGTH, (length -  offset));
    }

    /**
     * 使用整个文件创建零拷贝响应对象。
     *
     * @param file 待传输的文件
     */
    public ZeroCopyResponse(File file) {
        this(file, 0, file.length());
    }

    /**
     * 使用文件通道及指定偏移量和长度创建零拷贝响应对象。
     *
     * @param fileChannel 文件通道
     * @param offset      文件传输起始偏移量
     * @param length      文件传输长度
     * @param filename    响应文件名
     */
    public ZeroCopyResponse(FileChannel fileChannel, long offset, long length, String filename) {
        super(HttpResponseStatus.OK, filename, GlobalConfig.getResponseCharset());
        fileRegion = new DefaultFileRegion(fileChannel, offset, length);
        this.headers().add(HttpHeaderNames.CONTENT_LENGTH, (length -  offset));
    }

    /**
     * 使用整个文件通道创建零拷贝响应对象，并指定响应文件名。
     *
     * @param fileChannel 文件通道
     * @param filename    响应文件名
     * @throws IOException 获取文件通道大小失败时抛出
     */
    public ZeroCopyResponse(FileChannel fileChannel, String filename) throws IOException {
        this(fileChannel, 0, fileChannel.size(), filename);
    }

    /**
     * 使用整个文件通道创建零拷贝响应对象，文件名自动生成。
     *
     * @param fileChannel 文件通道
     * @throws IOException 获取文件通道大小失败时抛出
     */
    public ZeroCopyResponse(FileChannel fileChannel) throws IOException {
        this(fileChannel, UUID.randomUUID().toString());
    }

    /**
     * 获取文件传输区域对象。
     *
     * @return {@link FileRegion} 对象，用于零拷贝传输
     */
    public FileRegion getFileRegion() {
        return fileRegion;
    }

    /**
     * 获取内部调用类型。
     *
     * @return {@link InternalCallType#ZERO_COPY}
     */
    @Override
    public InternalCallType getType() {
        return InternalCallType.ZERO_COPY;
    }
}
