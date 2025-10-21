package top.turboweb.http.response;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import top.turboweb.commons.config.GlobalConfig;
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
 * HTTP 文件下载响应封装类。
 * <p>
 * 支持两种模式：
 * <ul>
 *     <li>直接从内存字节数组生成响应</li>
 *     <li>从磁盘文件生成响应，可自动流式处理大文件</li>
 * </ul>
 * <p>
 * 提供了多种静态工厂方法，用于快速生成图片、视频或普通文件下载响应。
 * 文件名会自动 URL 编码，保证浏览器下载兼容性。
 */
public class HttpFileResult {

    /** 文件内容缓冲区（仅在内存字节模式下使用） */
    private final ByteBuffer buffer;

    /** 文件名，已经经过 URL 编码处理 */
    private final String filename;

    /** MIME 类型，如 "image/png" 或 "application/octet-stream" */
    private final String contentType;

    /** 磁盘文件对象（仅在文件模式下使用） */
    private final File file;

    /** 是否在浏览器中直接打开文件（inline） */
    private final boolean openFile;

    /** 文件大小限制，超过限制则以流方式下载，默认 32MB */
    private long maxLimitSize = 33554432;

    /**
     * 内存字节数组构造方法。
     *
     * @param buffer      文件内容字节缓冲区，不能为空
     * @param filename    文件名，可为 null 或空字符串
     * @param contentType 文件 MIME 类型，可为 null
     * @param openFile    是否在浏览器中直接打开文件
     */
    public HttpFileResult(ByteBuffer buffer, String filename, String contentType, boolean openFile) {
        Objects.requireNonNull(buffer, "buffer can not be null");
        this.buffer = buffer;
        if (filename != null && !filename.isEmpty()) {
            this.filename = URLEncoder.encode(filename, GlobalConfig.getResponseCharset()).replace("+", "%20");
        } else {
            this.filename = UUID.randomUUID().toString();
        }
        this.contentType = contentType;
        file = null;
        this.openFile = openFile;
    }

    /**
     * 磁盘文件构造方法。
     *
     * @param file        磁盘文件对象，不能为空，且必须存在且可读
     * @param contentType 文件 MIME 类型，可为 null
     * @param openFile    是否在浏览器中直接打开文件
     * @throws IllegalArgumentException 文件不存在、不可读或是目录时抛出
     */
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
        filename = URLEncoder.encode(file.getName(), GlobalConfig.getResponseCharset()).replace("+", "%20");
        this.openFile = openFile;
    }

    /**
     * 关闭文件大小限制，确保大文件不会以流式响应。
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
     * 创建 HTTP 响应对象。
     * <p>
     * <ul>
     *     <li>如果是磁盘文件且超过大小限制，则以文件流方式响应（FileStreamResponse）</li>
     *     <li>否则，将文件或字节数组内容直接写入 FullHttpResponse</li>
     * </ul>
     *
     * @return HTTP 响应对象
     * @throws TurboFileException 文件读取异常时抛出
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
     * 设置响应头中的文件名。
     *
     * @param response HTTP 响应对象
     */
    private void setFilename(HttpResponse response) {
        if (openFile) {
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline;filename=\"" + filename + "\"");
        } else {
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment;filename=\"" + filename + "\"");
        }
    }
}
