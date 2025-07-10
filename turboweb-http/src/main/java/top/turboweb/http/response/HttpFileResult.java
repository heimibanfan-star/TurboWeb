package top.turboweb.http.response;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.exception.TurboFileException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 用于文件下载
 */
public class HttpFileResult {

    private final ByteBuffer buffer;
    private final String filename;
    private final String contentType;
    private final File file;
    private final boolean openFile;

    private long maxLimitSize = 33554432;

    public HttpFileResult(ByteBuffer buffer, String filename, String contentType, boolean openFile) {
        Objects.requireNonNull(buffer, "buffer can not be null");
        this.buffer = buffer;
        if (filename != null && !filename.isEmpty()) {
            this.filename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        } else {
            this.filename = UUID.randomUUID().toString();
        }
        this.contentType = contentType;
        file = null;
        this.openFile = openFile;
    }

    public HttpFileResult(File file, String contentType, boolean openFile) {
        Objects.requireNonNull(file, "file can not be null");
        if (!file.exists()) {
            throw new IllegalArgumentException("file not exists");
        }
        if (file.isDirectory()) {
            throw new IllegalArgumentException("file is directory");
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("file can not read");
        }
        this.file = file;
        this.contentType = contentType;
        buffer = null;
        filename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        this.openFile = openFile;
    }

    /**
     * 关闭文件大小限制
     */
    public void closeLimit() {
        maxLimitSize = -1;
    }

    public static HttpFileResult file(File file, String contentType, boolean openFile) {
        return new HttpFileResult(file, contentType, openFile);
    }

    public static HttpFileResult file(File file) {
        return new HttpFileResult(file, null, false);
    }

    public static HttpFileResult bytes(byte[] bytes, String filename, String contentType, boolean openFile) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, contentType, openFile);
    }

    public static HttpFileResult bytes(byte[] bytes, String filename) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, null, false);
    }

    public static HttpFileResult bytes(byte[] bytes) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), null, null, false);
    }

    public static HttpFileResult png(byte[] bytes, String filename) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, "image/png", true);
    }

    public static HttpFileResult png(byte[] bytes) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), null, "image/png", true);
    }

    public static HttpFileResult jpeg(byte[] bytes, String filename) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, "image/jpeg", true);
    }

    public static HttpFileResult jpeg(byte[] bytes) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), null, "image/jpeg", true);
    }

    public static HttpFileResult gif(byte[] bytes, String filename) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, "image/gif", true);
    }

    public static HttpFileResult gif(byte[] bytes) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), null, "image/gif", true);
    }

    public static HttpFileResult mp4(byte[] bytes, String filename) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), filename, "video/mp4", true);
    }

    public static HttpFileResult mp4(byte[] bytes) {
        return new HttpFileResult(ByteBuffer.wrap(bytes), null, "video/mp4", true);
    }

    /**
     * 创建文件下载响应
     *
     * @return 文件下载响应
     */
    public HttpResponse createResponse() {
        // 如果是磁盘文件，并且超过了限制大小，那么以文件流的形式响应
        if (file != null && maxLimitSize >= 0 && file.length() > maxLimitSize) {
            FileStreamResponse fileStreamResponse = new FileStreamResponse(file);
            if (contentType != null && !contentType.isEmpty()) {
                fileStreamResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            }
            // 设置文件名
            setFilename(fileStreamResponse);
            return fileStreamResponse;
        }
        // 判断是否是磁盘文件
        ByteBuffer byteBuffer;
        if (buffer != null) {
            byteBuffer = buffer;
        } else {
            if (file == null) {
                throw new IllegalArgumentException("buffer and file cannot both be null");
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                byteBuffer = ByteBuffer.wrap(fis.readAllBytes());
            } catch (IOException e) {
                throw new TurboFileException(e);
            }
        }
        // 创建响应对象
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(byteBuffer));
        if (contentType != null && !contentType.isEmpty()) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
        }
        // 设置文件名
        setFilename(response);
        // 设置响应长度
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    /**
     * 设置文件名
     *
     * @param response 响应对象
     */
    private void setFilename(HttpResponse response) {
        if (openFile) {
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline;filename=\"" + filename + "\"");
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment;filename=\"" + filename + "\"");
        }
    }
}
