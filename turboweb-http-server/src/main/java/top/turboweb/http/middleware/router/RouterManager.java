package top.turboweb.http.middleware.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboRequestException;
import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.RouterDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * TurboWeb用于管理controller路由的抽象类
 */
public abstract class RouterManager extends Middleware {

    private static final Logger log = LoggerFactory.getLogger(RouterManager.class);

    @Override
    public Object invoke(HttpContext ctx) {
        // 匹配路由的定义信息
        RouterDefinition routerDefinition = matchDefinition(ctx);
        // 如果没有路由定义信息，抛出异常
        if (routerDefinition == null) {
            throw new TurboRouterException(
                    String.format("router not found: %s %s", ctx.getRequest().method().name(), ctx.getRequest().uri()),
                    TurboRouterException.ROUTER_NOT_MATCH
            );
        }
        // 调用路由定义
        try {
            return routerDefinition.invoke(ctx);
        } catch (Throwable e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new TurboRouterException(e, TurboRouterException.ROUTER_INVOKE_ERROR);
            }
        }
    }

    protected abstract RouterContainer getRouterContainer();

    /**
     * 子类通过实现该方法根据自身策略匹配路由定义信息
     *
     * @param ctx 请求的上下文
     * @return 路由的定义信息
     */
    private RouterDefinition matchDefinition(HttpContext ctx) {
        // 获取请求方式
        String method = ctx.getRequest().method().name();
        // 获取请求的路径
        String path = ctx.getRequest().uri();
        // 数据校验
        if (method == null || path == null || method.isEmpty() || path.isEmpty()) {
            throw new TurboRequestException("request method and request uri is null or empty");
        }
        // 尝试进行精确匹配
        RouterDefinition routerDefinition = getRouterContainer().exactMatch(method, path);
        if (routerDefinition != null) {
            return routerDefinition;
        }
        // 当精确匹配无法匹配到时进行参数模式匹配
        RouterContainer.TrieMatchResult matchResult = getRouterContainer().trieMatch(method, path);
        if (matchResult != null) {
            // 解析路径参数
            parsePathVariable(ctx, matchResult);
            return matchResult.definition();
        }
        return null;
    }

    /**
     * 解析路径参数
     *
     * @param ctx              上下文
     * @param trieMatchResult 前缀树匹配结果
     */
    private void parsePathVariable(HttpContext ctx, RouterContainer.TrieMatchResult trieMatchResult) {
        Map<String, String> params = trieMatchResult.params();
        if (params == null) {
            params = Map.of();
        }
        ctx.injectPathParam(params);
    }

    @Override
    public void init(Middleware chain) {
        if (getNext() != null) {
            log.warn("RouterManager can not set next middleware");
        }
    }
}
