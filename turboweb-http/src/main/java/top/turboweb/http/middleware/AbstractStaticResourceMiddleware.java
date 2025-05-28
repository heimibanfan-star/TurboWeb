package top.turboweb.http.middleware;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.aware.MainClassAware;
import top.turboweb.http.response.HttpInfoResponse;
import top.turboweb.commons.exception.TurboStaticResourceException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 静态资源处理中间件的抽象类
 */
public abstract class AbstractStaticResourceMiddleware extends Middleware implements MainClassAware {

    private static final Logger log = LoggerFactory.getLogger(AbstractStaticResourceMiddleware.class);


    // 主启动类
    private Class<?> mainClass;
    // 默认的静态请求路径
    protected String staticResourceUri = "/static";
    // 静态资源路径
    protected String staticResourcePath = "static";
    // 是否缓存静态文件
    protected boolean cacheStaticResource = true;
    // 多少字节以内的文件进行缓存
    private int cacheFileSize = 1024 * 1024;
    // 用于缓存静态文件的缓存
    protected final Map<String, byte[]> caches = new ConcurrentHashMap<>();
    protected final Tika tika = new Tika();

    /**
     * 从文件中加载静态资源并缓存
     *
     * @param path 文件路径
     * @return 文件字节数组
     */
    protected byte[] loadAndCacheStaticResource(String path) {
        path = safePath(path);
        // 从文件中读取
        InputStream inputStream = mainClass.getClassLoader().getResourceAsStream(path);
        try (inputStream) {
            if (inputStream != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) > 0) {
                    baos.write(buffer, 0, read);
                }
                byte[] bytes = baos.toByteArray();
                if (cacheStaticResource) {
                    if (bytes.length < cacheFileSize) {
                        caches.put(path, bytes);
                    }
                }
                return bytes;
            }
        } catch (Exception e) {
            log.error("file load error: {}", path, e);
        }
        return null;
    }

    @Override
    public void setMainClass(Class<?> mainClass) {
        this.mainClass = mainClass;
    }

    /**
     * 构建响应对象
     *
     * @param ctx      上下文
     * @param bytes    文件字节数组
     * @param path     文件路径
     * @return 响应对象
     */
    protected HttpInfoResponse buildResponse(HttpContext ctx, byte[] bytes, String path) {
        // 构建响应对象
        HttpInfoResponse response = new HttpInfoResponse(
            ctx.getRequest().getProtocolVersion(),
            HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(bytes)
        );
        response.headers().set(ctx.getResponse().headers());
        // 设置文件类型
        String mimeType = tika.detect(path);
        log.debug("file:{}, type:[{}]", path, mimeType);
        response.setContentType(mimeType);
        // 设置响应内容的大小
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        return response;
    }

    /**
     * 校验路径是否安全
     *
     * @param path 路径
     */
    public String safePath(String path) {
        try {
            // URL 解码，防止编码绕过
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            Path basePath = Paths.get(staticResourcePath);
            Path normalizedPath = Paths.get(decodedPath).normalize();

            if (normalizedPath.isAbsolute()) {
                log.warn("非法绝对路径请求: {}", path);
                throw new TurboStaticResourceException("非法绝对路径请求");
            }

            if (!normalizedPath.startsWith(basePath)) {
                log.warn("非法路径穿透请求: {}", path);
                throw new TurboStaticResourceException("非法路径穿透请求");
            }
            return normalizedPath.toString();
        } catch (IllegalArgumentException e) {
            log.warn("路径解码异常: {}", path);
            throw new TurboStaticResourceException("路径解码异常");
        }
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
