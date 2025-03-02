package org.turbo.web.core.http.middleware;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.aware.MainClassAware;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.exception.TurboRouterException;
import org.turbo.web.exception.TurboStaticResourceException;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理静态资源的中间件
 */
public class StaticResourceMiddleware extends Middleware implements MainClassAware {

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
    // 用于缓存静态文件的缓存
    private final Map<String, byte[]> caches = new ConcurrentHashMap<>();
    private final Tika tika = new Tika();

    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求的路径
        String uri = ctx.getRequest().getUri();
        if (uri.startsWith(staticResourceUri)) {
            if (uri.contains("?")) {
                uri = uri.substring(0, uri.indexOf("?"));
            }
            // 去除前缀
            uri = uri.replace(staticResourceUri, staticResourcePath);
            byte[] bytes = loadAndCacheStaticResource(uri);
            if (bytes != null) {
                return buildResponse(ctx, bytes, uri);
            } else {
                throw new TurboRouterException("找不到静态资源", TurboRouterException.ROUTER_NOT_MATCH);
            }
        }
        return ctx.doNext();
    }

    /**
     * 从文件中加载静态资源并缓存
     *
     * @param path 文件路径
     * @return 文件字节数组
     */
    private byte[] loadAndCacheStaticResource(String path) {
        // 从缓存中获取
        if (cacheStaticResource) {
            byte[] bytes = caches.get(path);
            if (bytes != null) {
                return bytes;
            }
        }
        // 从文件中读取
        InputStream inputStream = mainClass.getClassLoader().getResourceAsStream(path);
        try (inputStream) {
            if (inputStream != null) {
                byte[] bytes = new byte[inputStream.available()];
                int read = inputStream.read(bytes);
                if (read != bytes.length) {
                    throw new TurboStaticResourceException("文件读取失败,真实长度:%d,读取到的长度:%d".formatted(bytes.length, read));
                }
                if (cacheStaticResource) {
                    if (bytes.length < cacheFileSize) {
                        caches.put(path, bytes);
                    }
                }
                return bytes;
            }
        } catch (Exception e) {
            log.error("文件读取失败: {}", path, e);
        }
        return null;
    }

    /**
     * 构建响应对象
     *
     * @param ctx      上下文
     * @param bytes    文件字节数组
     * @param path     文件路径
     * @return 响应对象
     */
    private HttpInfoResponse buildResponse(HttpContext ctx, byte[] bytes, String path) {
        // 构建响应对象
        HttpInfoResponse response = new HttpInfoResponse(
            ctx.getRequest().getProtocolVersion(),
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        // 设置文件类型
        String mimeType = tika.detect(path);
        response.setContentType(mimeType);
        // 设置响应内容的大小
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return response;
    }

    @Override
    public void setMainClass(Class<?> mainClass) {
        this.mainClass = mainClass;
    }

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
}
