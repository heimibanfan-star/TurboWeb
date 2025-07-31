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
 * 基于零拷贝实现的文件响应对象
 */
public class ZeroCopyResponse extends AbstractFileResponse implements InternalCallResponse {

    private final FileRegion fileRegion;

    public ZeroCopyResponse(File file, long offset, long length) {
        super(HttpResponseStatus.OK, file, GlobalConfig.getResponseCharset());
        fileRegion = new DefaultFileRegion(file, offset, length);
        this.headers().add(HttpHeaderNames.CONTENT_LENGTH, (length -  offset));
    }

    public ZeroCopyResponse(File file) {
        this(file, 0, file.length());
    }

    public ZeroCopyResponse(FileChannel fileChannel, long offset, long length, String filename) {
        super(HttpResponseStatus.OK, filename, GlobalConfig.getResponseCharset());
        fileRegion = new DefaultFileRegion(fileChannel, offset, length);
        this.headers().add(HttpHeaderNames.CONTENT_LENGTH, (length -  offset));
    }

    public ZeroCopyResponse(FileChannel fileChannel, String filename) throws IOException {
        this(fileChannel, 0, fileChannel.size(), filename);
    }

    public ZeroCopyResponse(FileChannel fileChannel) throws IOException {
        this(fileChannel, UUID.randomUUID().toString());
    }

    public FileRegion getFileRegion() {
        return fileRegion;
    }

    @Override
    public InternalCallType getType() {
        return InternalCallType.ZERO_COPY;
    }
}
