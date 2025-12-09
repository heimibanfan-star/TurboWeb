package top.turboweb.http.middleware;

import top.turboweb.http.context.HttpContext;
import top.turboweb.commons.exception.TurboRequestRejectException;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Paths;

/**
 * 哨兵节点的中间件
 */
public class SentinelMiddleware extends Middleware{
    @Override
    public Object invoke(HttpContext ctx) {
        return next(ctx);
    }
}
