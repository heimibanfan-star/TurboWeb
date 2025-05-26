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
            // 去除前缀
            uri = uri.replace(staticResourceUri, staticResourcePath);
            // 从缓存中获取
            if (cacheStaticResource) {
                byte[] bytes = caches.get(uri);
                if (bytes != null) {
                    return buildResponse(ctx, bytes, uri);
                }
            }
            byte[] bytes = loadAndCacheStaticResource(uri);
            if (bytes != null) {
                return buildResponse(ctx, bytes, uri);
            } else {
                throw new TurboRouterException("找不到静态资源", TurboRouterException.ROUTER_NOT_MATCH);
            }
        }
        return next(ctx);
    }

}
