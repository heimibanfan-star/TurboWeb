package top.turboweb.http.router.dispatcher.impl;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.container.RouterContainer;
import top.turboweb.http.router.container.info.RouterDefinition;
import top.turboweb.http.router.dispatcher.HttpDispatcher;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.commons.exception.TurboRequestException;
import top.turboweb.commons.exception.TurboRouterException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 默认的http分发器
 */
public class DefaultHttpDispatcher implements HttpDispatcher {

    private final RouterContainer routerContainer;
    public DefaultHttpDispatcher(RouterContainer routerContainer) {
        this.routerContainer = routerContainer;
    }

    @Override
    public Object dispatch(HttpContext ctx) {
        HttpInfoRequest request = ctx.getRequest();
        // 获取请求方式
        String requestMethod = request.getMethod();
        // 获取请求路径
        String path = request.getUri();
        if (Objects.isNull(requestMethod) || Objects.isNull(path) || requestMethod.isEmpty() || path.isEmpty()) {
            throw new TurboRequestException("请求路径或请求方式不能为空");
        }
        // 尝试进行精确路由匹配
        RouterDefinition routerDefinition = routerContainer.exactMatch(requestMethod, path);
        if (routerDefinition == null) {
            RouterContainer.TrieMatchResult trieMatchResult = routerContainer.trieMatch(requestMethod, path);
            if (trieMatchResult == null) {
                throw new TurboRouterException("未匹配到对应的路由: %s %s".formatted(requestMethod, path), TurboRouterException.ROUTER_NOT_MATCH);
            }
            // 注入路径参数
            parsePathVariable(ctx, trieMatchResult);
            routerDefinition = trieMatchResult.definition();
        }
        // 调用方法
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

    /**
     * 解析路径参数
     *
     * @param ctx              上下文
     * @param trieMatchResult 前缀树匹配结果
     */
    private void parsePathVariable(HttpContext ctx, RouterContainer.TrieMatchResult trieMatchResult) {
        Map<String, String> params = trieMatchResult.params();
        if (params == null) {
            params = new HashMap<>(1);
        }
        ctx.injectPathParam(params);
    }
}
