package org.turbo.web.core.http.middleware;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.aware.MainClassAware;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.exception.TurboRouterException;
import org.turbo.web.exception.TurboStaticResourceException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反应式静态资源处理中间件
 * TODO DEV
 */
public class ReactiveStaticResourceMiddleware extends ReactiveMiddleware implements MainClassAware {
    private static final Logger log = LoggerFactory.getLogger(StaticResourceMiddleware.class);
    // 主启动类
    private Class<?> mainClass;
    // 默认的静态请求路径
    private String staticResourceUri = "/static";
    // 静态资源路径
    private String staticResourcePath = "static";
    // 是否缓存静态文件
    private boolean cacheStaticResource = true;
    // 多少字节以内的文件进行缓存
    private int cacheFileSize = 1024 * 1024;

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    // 用于缓存静态文件的缓存
    private final Tika tika = new Tika();

    public void setStaticResourceUri(String staticResourceUri) {
        this.staticResourceUri = staticResourceUri;
    }

    public void setStaticResourcePath(String staticResourcePath) {
        this.staticResourcePath = staticResourcePath;
    }

    public void setCacheStaticResource(boolean cacheStaticResource) {
        this.cacheStaticResource = cacheStaticResource;
    }

    public void setCacheFileSize(int cacheFileSize) {
        this.cacheFileSize = cacheFileSize;
    }

    @Override
    public Mono<?> doSubscribe(HttpContext ctx) {
        String uri = ctx.getRequest().getUri();
        if (!uri.startsWith(staticResourceUri)) {
            return ctx.doNextMono();
        }
        return readStaticResource(ctx.getResponse().headers(), uri);
    }

    private Mono<HttpInfoResponse> readStaticResource(HttpHeaders headers, String uri) {
        // 去掉？以及后续内容
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        String path = uri.replace(staticResourceUri, staticResourcePath);
        if (cache.get(path) != null) {
            byte[] buffer = cache.get(path);
            return Mono.create(sink ->{
               HttpInfoResponse response = new HttpInfoResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(buffer)
                    );
               response.headers().set(headers);
               response.setContentType(tika.detect(path));
               response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.length);
               sink.success(response);
            });
        }
        // 获取文件路径
        URL url = mainClass.getClassLoader().getResource(path);
        // 获取路径
        if (url == null) {
            throw new TurboRouterException("静态资源路径不存在", TurboRouterException.ROUTER_NOT_MATCH);
        }
        // 读取文件内容
        return Mono.create(sink -> {
            try {
                Path filePath = Paths.get(new URI(url.toString()));
                AsynchronousFileChannel aioFileChannel = AsynchronousFileChannel.open(filePath, StandardOpenOption.READ);
                // 分配缓冲区
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                aioFileChannel.read(buffer, 0, buffer, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        if (result > 0) {
                            attachment.flip();
                            ByteBuf content;
                            if (cacheStaticResource && result < cacheFileSize) {
                                byte[] bytes = new byte[result];
                                attachment.get(bytes);
                                cache.put(path, bytes);
                                content = Unpooled.wrappedBuffer(bytes);
                            } else {
                                content = Unpooled.wrappedBuffer(attachment);
                            }
                            HttpInfoResponse response = new HttpInfoResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content
                            );
                            response.headers().set(headers);
                            response.setContentType(tika.detect(path));
                            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
                            sink.success(response);
                        }
                        try {
                            aioFileChannel.close();
                        } catch (IOException e) {
                            throw new TurboStaticResourceException("资源关闭失败", e);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        sink.error(exc);
                        try {
                            aioFileChannel.close();
                        } catch (IOException e) {
                            throw new TurboStaticResourceException("资源关闭失败", e);
                        }
                    }
                });
            } catch (IOException e) {
                sink.error(new TurboStaticResourceException("资源打开失败", e));
            } catch (URISyntaxException e) {
                sink.error(new TurboStaticResourceException("路径解析失败", e));
            }
        });
    }

    @Override
    public void setMainClass(Class<?> mainClass) {
        this.mainClass = mainClass;
    }
}
