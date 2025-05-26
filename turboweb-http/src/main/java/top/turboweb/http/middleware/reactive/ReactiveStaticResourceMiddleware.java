package top.turboweb.http.middleware.reactive;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.AbstractStaticResourceMiddleware;
import top.turboweb.http.response.HttpInfoResponse;
import reactor.core.publisher.Mono;

/**
 * 反应式静态资源处理中间件
 */
public class ReactiveStaticResourceMiddleware extends AbstractStaticResourceMiddleware {
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
            String filePath = uri;
            return Mono.create(sink -> {
                // 从缓存中获取
                if (cacheStaticResource) {
                    byte[] bytes = caches.get(filePath);
                    if (bytes != null) {
                        HttpInfoResponse response = buildResponse(ctx, bytes, filePath);
                        sink.success(response);
                        return;
                    }
                }
                Thread.ofVirtual().start(() ->{
                    try {
                        byte[] bytes = loadAndCacheStaticResource(filePath);
                        HttpInfoResponse response = buildResponse(ctx, bytes, filePath);
                        sink.success(response);
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
            });
        }
        return next(ctx);
    }
}
