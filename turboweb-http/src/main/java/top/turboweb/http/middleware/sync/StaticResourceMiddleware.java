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

    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求的路径
        String uri = ctx.getRequest().getUri();
        if (uri.startsWith(staticResourceUri)) {
            return loadStaticAndBuild(ctx);
        }
        return next(ctx);
    }

}
