package top.turboweb.http.middleware.view;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.commons.exception.TurboStaticResourceException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态资源处理中间件的抽象类
 */
public abstract class AbstractStaticResourceMiddleware extends Middleware {

    private static final Logger log = LoggerFactory.getLogger(AbstractStaticResourceMiddleware.class);

    private static class ResourceCache {
        byte[] bytes;
        String mimeType;
    }

    // 默认的静态请求路径
    protected String staticResourceUri = "/static";
    // 静态资源路径
    private String staticResourcePath = "/static";
    // 是否缓存静态文件
    protected boolean cacheStaticResource = true;
    // 多少字节以内的文件进行缓存
    private int cacheFileSize = 1024 * 1024;
    // 用于缓存静态文件的缓存
    private final Map<String, ResourceCache> caches = new ConcurrentHashMap<>();
    private final Tika tika = new Tika();
    private final ClassLoader classLoader;

    public AbstractStaticResourceMiddleware(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public AbstractStaticResourceMiddleware() {
        this(AbstractStaticResourceMiddleware.class.getClassLoader());
    }

    /**
     * 处理静态资源
     *
     * @param c 上下文
     * @return 响应对象
     */
    protected HttpResponse loadStaticAndBuild(HttpContext c) {
        String originUri = c.getRequest().getUri();
        String handledUri = handleUri(originUri);
        // 转化为标准路径
        Path path = safePath(handledUri);
        log.debug("Mapped URI: [{}] to PATH [{}]", originUri, path);
        // 判断是否启用缓存
        if (cacheStaticResource) {
            // 从缓存中加载数据
            ResourceCache resourceCache = caches.get(path.toString());
            if (resourceCache != null) {
                return buildResponse(c, resourceCache);
            }
        }
        ResourceCache resourceCache = loadAndCacheStaticResource(path.toString());
        return buildResponse(c, resourceCache);
    }

    /**
     * 替换uri为path
     *
     * @param uri 请求的uri
     * @return 文件的路径
     */
    private String handleUri(String uri) {
        // 去除路径之后的查询参数
        if (uri.contains("?")) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        String tempUri = uri.substring(staticResourceUri.length());
        if (tempUri.startsWith("/")) {
            return tempUri.substring(1);
        }
        return tempUri;
    }

    /**
     * 从文件中加载静态资源并缓存
     *
     * @param path 文件路径
     * @return 文件缓存数据
     */
    private ResourceCache loadAndCacheStaticResource(String path) {
        String diskPath = path;
        diskPath = diskPath.replace("\\", "/");
        if (diskPath.startsWith("/")) {
            diskPath = diskPath.substring(1);
        }
        // 从文件中读取
        InputStream inputStream = classLoader.getResourceAsStream(diskPath);
        try (inputStream) {
            if (inputStream != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    baos.write(buffer, 0, read);
                }
                byte[] bytes = baos.toByteArray();
                ResourceCache cache = new ResourceCache();
                cache.bytes = bytes;
                if (cacheStaticResource) {
                    if (cache.bytes.length < cacheFileSize) {
                        cache.mimeType = tika.detect(bytes, path);
                        caches.put(path, cache);
                    }
                }
                if (cache.mimeType == null) {
                    cache.mimeType = tika.detect(path);
                }
                return cache;
            }
        } catch (Exception e) {
            throw new TurboStaticResourceException("file read error, path:" + path, e);
        }
        throw new TurboRouterException("file not found for path:" + path, TurboRouterException.ROUTER_NOT_MATCH);
    }

    /**
     * 构建响应对象
     *
     * @param ctx      上下文
     * @param cache    文件数据
     * @return 响应对象
     */
    private HttpInfoResponse buildResponse(HttpContext ctx, ResourceCache cache) {
        // 构建响应对象
        HttpInfoResponse response = new HttpInfoResponse(
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(cache.bytes)
        );
        response.setContentType(cache.mimeType);
        // 设置响应内容的大小
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, cache.bytes.length);
        return response;
    }

    /**
     * 校验路径是否安全
     *
     * @param path 路径
     */
    private Path safePath(String path) {
        try {
            // URL 解码，防止编码绕过
            String decodedPath = URLDecoder.decode(path, GlobalConfig.getRequestCharset());
            Path basePath = Paths.get(staticResourcePath);
            Path normalizedPath = basePath.resolve(decodedPath).normalize();

            if (!normalizedPath.startsWith(basePath)) {
                log.warn("非法路径穿透请求: {}", path);
                throw new TurboStaticResourceException("非法路径穿透请求");
            }
            return normalizedPath;
        } catch (IllegalArgumentException e) {
            log.warn("路径解码异常: {}", path);
            throw new TurboStaticResourceException("路径解码异常");
        }
    }


    public void setStaticResourceUri(String staticResourceUri) {
        this.staticResourceUri = staticResourceUri;
    }

    public void setStaticResourcePath(String staticResourcePath) {
        if (!staticResourcePath.startsWith("/")) {
            staticResourcePath = "/" + staticResourcePath;
        }
        this.staticResourcePath = staticResourcePath;
    }

    public void setCacheStaticResource(boolean cacheStaticResource) {
        this.cacheStaticResource = cacheStaticResource;
    }

    public void setCacheFileSize(int cacheFileSize) {
        this.cacheFileSize = cacheFileSize;
    }
}
