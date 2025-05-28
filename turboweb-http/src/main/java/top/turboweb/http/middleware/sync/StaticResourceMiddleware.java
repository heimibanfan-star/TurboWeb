package top.turboweb.http.middleware.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.AbstractStaticResourceMiddleware;
import top.turboweb.commons.exception.TurboRouterException;

/**
 * 处理静态资源的中间件
 */
public class StaticResourceMiddleware extends AbstractStaticResourceMiddleware {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceMiddleware.class);


    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求的路径
        String uri = ctx.getRequest().getUri();
        if (uri.startsWith(staticResourceUri)) {
            if (uri.contains("?")) {
                uri = uri.substring(0, uri.indexOf("?"));
            }
            String path = replaceUriToPath(uri);
            log.debug("Mapped URI [{}] TO PATH [{}]", uri, path);
            // 从缓存中获取
            if (cacheStaticResource) {
                byte[] bytes = caches.get(path);
                if (bytes != null) {
                    return buildResponse(ctx, bytes, path);
                }
            }
            byte[] bytes = loadAndCacheStaticResource(path);
            if (bytes != null) {
                return buildResponse(ctx, bytes, path);
            } else {
                throw new TurboRouterException("找不到静态资源", TurboRouterException.ROUTER_NOT_MATCH);
            }
        }
        return next(ctx);
    }

    /**
     * 替换uri为path
     *
     * @param uri 请求的uri
     * @return 文件的路径
     */
    private String replaceUriToPath(String uri) {
        String tempUri = uri.substring(staticResourceUri.length());
        // 处理/拼接的问题
        if (!staticResourcePath.endsWith("/") && !tempUri.startsWith("/")) {
            return staticResourcePath + "/" + tempUri;
        }
        // 防止出现多余的/
        if (staticResourcePath.endsWith("/") && tempUri.startsWith("/")) {
            return staticResourcePath + tempUri.substring(1);
        }
        return staticResourcePath + tempUri;
    }

}
