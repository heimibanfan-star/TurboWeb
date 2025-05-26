package top.turboweb.http.middleware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.context.HttpContext;
import top.turboweb.commons.exception.TurboReactiveException;
import reactor.core.publisher.Mono;

/**
 * 中间件接口
 */
public abstract class Middleware extends BaseMiddleware {

    private static final Logger log = LoggerFactory.getLogger(Middleware.class);


    /**
     * 中间件执行方法
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    public abstract Object invoke(HttpContext ctx);

    /**
     * 执行下一个中间件
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    protected Object next(HttpContext ctx) {
        if (getNext() == null) {
            return null;
        } else {
            return getNext().invoke(ctx);
        }
    }

    /**
     * 执行下一个中间件
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    protected Mono<?> nextMono(HttpContext ctx) {
        Object result = next(ctx);
        if (result instanceof Mono<?> mono) {
            return mono;
        } else {
            throw new TurboReactiveException("TurboWeb仅支持Mono类型的反应式对象");
        }
    }
}
