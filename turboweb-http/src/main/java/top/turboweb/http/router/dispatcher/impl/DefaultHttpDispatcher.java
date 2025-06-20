package top.turboweb.http.router.dispatcher.impl;

import top.turboweb.commons.constants.MatchType;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.dispatcher.HttpDispatcher;
import top.turboweb.http.request.HttpInfoRequest;
import top.turboweb.http.router.definition.RouterMethodDefinition;
import top.turboweb.http.router.matcher.MatchResult;
import top.turboweb.http.router.matcher.RouterMatcher;
import top.turboweb.commons.exception.TurboRequestException;
import top.turboweb.commons.exception.TurboRouterException;

import java.lang.invoke.MethodHandle;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认的http分发器
 */
public class DefaultHttpDispatcher implements HttpDispatcher {

    private final RouterMatcher routerMatcher;
    public DefaultHttpDispatcher(RouterMatcher routerMatcher) {
        this.routerMatcher = routerMatcher;
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
        MatchResult matchResult = routerMatcher.match(requestMethod, path);
        RouterMethodDefinition methodDefinition = matchResult.getDefinition();
        if (methodDefinition == null) {
            throw new TurboRouterException("未匹配到对应的路由: %s %s".formatted(requestMethod, path), TurboRouterException.ROUTER_NOT_MATCH);
        }
        // 判断是否需要解析路径参数
        if (MatchType.PATH.equals(matchResult.getMatchType())) {
            parsePathVariable(ctx, methodDefinition);
        }
        // 获取方法
        MethodHandle methodHandle = methodDefinition.getMethod();
        // 调用方法
		try {
			return methodHandle.invoke(ctx);
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
     * @param methodDefinition 方法定义
     */
    private void parsePathVariable(HttpContext ctx, RouterMethodDefinition methodDefinition) {
        // 判断是否需要解析路径参数
        if (methodDefinition.getPathVariableCount() == 0) {
            return;
        }
        // 获取匹配模板
        Pattern pattern = methodDefinition.getPattern();
        // 处理路径
        String path = ctx.getRequest().getUri();
        URI uri = URI.create(path);
        path = uri.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        Matcher matcher = pattern.matcher(path);
        if (!matcher.find()) {
            return;
        }
        Map<String, String> pathVariables = new HashMap<>(1);
        // 获取参数列表
        List<String> pathParameters = methodDefinition.getPathParameters();
        int count = matcher.groupCount();
        int index = 0;
        while (index < count && index < pathParameters.size()) {
            String paramName = pathParameters.get(index);
            String paramValue = matcher.group(index + 1);
            pathVariables.put(paramName, paramValue);
            index++;
        }
        ctx.injectPathParam(pathVariables);
    }
}
