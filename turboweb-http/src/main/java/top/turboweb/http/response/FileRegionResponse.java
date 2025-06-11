package top.turboweb.http.response;

import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoop;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.utils.base.HttpResponseUtils;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.connect.InternalConnectSession;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 实现零拷贝
 */
public class FileRegionResponse extends AbstractFileResponse{

    private static final Logger log = LoggerFactory.getLogger(FileRegionResponse.class);
    private final FileRegion fileRegion;
    private final File file;
    private final boolean autoDegenerate;

    public FileRegionResponse(HttpVersion version, HttpResponseStatus status, File file, Charset filenameCharset, boolean autoDegenerate) {
        super(version, status, file, filenameCharset);
        this.fileRegion = new DefaultFileRegion(file, 0, file.length());
        this.file = file;
        this.autoDegenerate = autoDegenerate;
        this.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
    }

    public FileRegionResponse(File file, Charset filenameCharset) {
        this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, filenameCharset, true);
    }

    public FileRegionResponse(File file) {
        this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, true);
    }

    public FileRegionResponse(File file, boolean autoDegenerate) {
        this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, file, StandardCharsets.UTF_8, autoDegenerate);
    }

    /**
     * 获取FileRegionResponse
     */
    public AbstractFileResponse getFileResponse(ConnectSession session) {
        InternalConnectSession internalConnectSession = (InternalConnectSession) session;
        EventLoop executor = internalConnectSession.getExecutor();
        if (executor.inEventLoop() || !autoDegenerate) {
            return this;
        }
        log.debug("FileRegionResponse is not in event loop, degenerate to FileStreamResponse");
        return degenerate();
    }

    public FileRegion getFileRegion() {
        return fileRegion;
    }

    /**
     * 降级为FileStreamResponse
     */
    private AbstractFileResponse degenerate() {
        FileStreamResponse fileStreamResponse = new FileStreamResponse(file, true);
        HttpResponseUtils.mergeHeaders(this, fileStreamResponse);
        fileStreamResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
        return fileStreamResponse;
    }

}
