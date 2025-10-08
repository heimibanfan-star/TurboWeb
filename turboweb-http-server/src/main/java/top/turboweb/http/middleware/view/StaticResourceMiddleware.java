package top.turboweb.http.middleware.view;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.hc.core5.http.ContentType;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.exception.TurboStaticResourceException;
import top.turboweb.commons.utils.thread.DiskOpeThreadUtils;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.response.FileStreamResponse;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.http.response.ZeroCopyResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理静态资源的中间件
 */
public class StaticResourceMiddleware extends Middleware {


    private static final Logger log = LoggerFactory.getLogger(StaticResourceMiddleware.class);


    private static class ResourceCache {
        byte[] bytes;
        String mimeType;
    }

    // 默认的静态请求路径
    protected String requestUrl = "/static";
    // 静态资源路径
    private String staticResourcePath = "/static";
    // 是否缓存静态文件
    protected boolean cacheStaticResource = true;
    // 是否是类路径中的文件
    private boolean inClasspath = true;
    // 是否开启零拷贝
    protected boolean zeroCopy = false;
    // 多少字节以内的文件进行缓存
    private int cacheFileSize = 1024 * 1024;
    // 用于缓存静态文件的缓存
    private final Map<String, ResourceCache> caches = new ConcurrentHashMap<>();
    private final Tika tika = new Tika();
    private final ClassLoader classLoader;

    private final Set<String> rangeTypes;
    private final Set<String> inlineTypes;

    {
        rangeTypes = new HashSet<>();
        rangeTypes.add("video/mp4");
        rangeTypes.add("video/webm");
        rangeTypes.add("video/ogg");
        rangeTypes.add("video/mpeg");
        rangeTypes.add("video/quicktime");
        rangeTypes.add("video/3gpp");
        rangeTypes.add("video/3gpp2");
        rangeTypes.add("video/x-msvideo");
        rangeTypes.add("audio/mp4");
        rangeTypes.add("audio/webm");
        rangeTypes.add("audio/ogg");
        rangeTypes.add("audio/mpeg");
        rangeTypes.add("audio/quicktime");
        rangeTypes.add("audio/3gpp");
        rangeTypes.add("audio/3gpp2");
        rangeTypes.add("audio/x-ms-wma");
        rangeTypes.add("audio/x-ms-wmv");
        rangeTypes.add("audio/x-ms-wm");
        rangeTypes.add("audio/x-ms-wax");
        rangeTypes.add("application/octet-stream");

        inlineTypes = new HashSet<>();
        inlineTypes.addAll(rangeTypes);
        inlineTypes.remove("application/octet-stream");
        inlineTypes.add("text/html");
        inlineTypes.add("text/css");
        inlineTypes.add("application/javascript");
        inlineTypes.add("image/png");
        inlineTypes.add("image/jpeg");
        inlineTypes.add("image/gif");
        inlineTypes.add("application/json");
        inlineTypes.add("application/xml");
        inlineTypes.add("text/plain");
    }

    private int maxRangeChunk = 1024 * 1024;

    public StaticResourceMiddleware(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public StaticResourceMiddleware(boolean inline) {
        this(StaticResourceMiddleware.class.getClassLoader());
    }

    public StaticResourceMiddleware() {
        this(StaticResourceMiddleware.class.getClassLoader());
    }

    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求的路径
        String uri = ctx.getRequest().uri();
        if (uri.startsWith(requestUrl)) {
            HttpResponse response =  loadStaticAndBuild(ctx);
            if (inlineTypes.contains(ContentType.parse(response.headers().get(HttpHeaderNames.CONTENT_TYPE)).getMimeType())) {
                response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "inline");
            }
            return response;
        }
        return next(ctx);
    }

    /**
     * 处理静态资源
     *
     * @param c 上下文
     * @return 响应对象
     */
    protected HttpResponse loadStaticAndBuild(HttpContext c) {
        String originUri = c.getRequest().uri();
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

        // 从磁盘中提取文件
        File file = loadFile(path.toString());
        // 校验文件
        if (!file.exists() || file.isDirectory()) {
            log.warn("file not found for path:" + path);
            throw new TurboRouterException("not found:" + c.getRequest().uri(), TurboRouterException.ROUTER_NOT_MATCH);
        }
        // 判断文件是否可以被缓存
        if (cacheStaticResource && file.length() < cacheFileSize) {
            // 预先将文件读取出来
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            DiskOpeThreadUtils.execute(() -> {
                try (FileInputStream fis = new FileInputStream(file)) {
                    future.complete(fis.readAllBytes());
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            });
            try {
                byte[] bytes = future.get();
                // 判断文件的类型
                String mimeType = tika.detect(bytes, path.toString());
                // 缓存文件
                ResourceCache resourceCache = new ResourceCache();
                resourceCache.bytes = bytes;
                resourceCache.mimeType = mimeType;
                caches.put(path.toString(), resourceCache);
                return buildResponse(c, resourceCache);
            } catch (Exception e) {
                throw new TurboStaticResourceException("file read error, path:" + path);
            }
        } else {
            // 获取文件类型
            try {
                String mimeType = tika.detect(file);
                HttpResponse response;
                // 获取范围
                boolean enableRange = false;
                long start = 0;
                long end = file.length() - 1;
                String range = c.getRequest().headers().get(HttpHeaderNames.RANGE);
                if (range != null && range.startsWith("bytes=")) {
                    String[] parts = range.substring(6).split("-");
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                    enableRange = true;
                }
                enableRange = enableRange && rangeTypes.contains(mimeType) && start >= 0 && end < file.length() && start <= end;
                // 缩放区间
                if (end - start > maxRangeChunk) {
                    end = start + maxRangeChunk - 1;
                }
                if (zeroCopy) {
                    if (enableRange) {
                        response = new ZeroCopyResponse(file, start, end - start + 1);
                    } else {
                        response = new ZeroCopyResponse(file);
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
                    }
                } else {
                    if (enableRange) {
                        response = new FileStreamResponse(file, start, end - start + 1);
                    } else {
                        response = new FileStreamResponse(file);
                        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
                    }
                }
                // 设置206状态码
                if (enableRange) {
                    response.setStatus(HttpResponseStatus.PARTIAL_CONTENT);
                    response.headers().set(HttpHeaderNames.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + file.length());
                    response.headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes");
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, end - start + 1);
                }
                // 设置必要的响应头
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeType);
                return response;
            } catch (IOException e) {
                throw new TurboStaticResourceException("file read error, path:" + path);
            }
        }
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
        String tempUri = uri.substring(requestUrl.length());
        if (tempUri.startsWith("/")) {
            return tempUri.substring(1);
        }
        return tempUri;
    }

    private File loadFile(String filePath) {
        String diskPath = filePath;
        diskPath = diskPath.replace("\\", "/");
        if (inClasspath) {
            diskPath = diskPath.substring(1);
            // 加载静态文件
            URL url = classLoader.getResource(diskPath);
            if (url != null) {
                try {
                    return Paths.get(url.toURI()).toFile();
                } catch (Exception e) {
                    throw new TurboStaticResourceException("file read error, path:" + diskPath, e);
                }
            } else {
                throw new TurboStaticResourceException("file not found for path:" + diskPath);
            }
        } else {
            return new File(diskPath);
        }
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


    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    /**
     * 设置静态资源路径
     *
     * @param staticResourcePath 静态资源路径
     */
    public void setStaticResourcePath(String staticResourcePath) {
        if (staticResourcePath.startsWith("classpath:")) {
            inClasspath = true;
            staticResourcePath = staticResourcePath.substring("classpath:".length());
            if (!staticResourcePath.startsWith("/")) {
                staticResourcePath = "/" + staticResourcePath;
            }
        } else {
            inClasspath = false;
        }
        this.staticResourcePath = staticResourcePath;
    }

    public void setCacheStaticResource(boolean cacheStaticResource) {
        this.cacheStaticResource = cacheStaticResource;
    }

    public void setCacheFileSize(int cacheFileSize) {
        this.cacheFileSize = cacheFileSize;
    }

    public void setZeroCopy(boolean zeroCopy) {
        this.zeroCopy = zeroCopy;
    }

    public void addRangeType(String... rangeTypes) {
        this.rangeTypes.addAll(Arrays.asList(rangeTypes));
    }

    public void removeRangeType(String... rangeTypes) {
        Arrays.asList(rangeTypes).forEach(this.rangeTypes::remove);
    }

    public void addInlineType(String... inlineTypes) {
        this.inlineTypes.addAll(Arrays.asList(inlineTypes));
    }

    public void removeInlineType(String... inlineTypes) {
        Arrays.asList(inlineTypes).forEach(this.inlineTypes::remove);
    }

    public void setMaxRangeChunk(int maxRangeChunk) {
        this.maxRangeChunk = maxRangeChunk;
    }
}
