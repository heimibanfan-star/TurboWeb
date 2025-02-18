package org.turbo.core.http.execetor.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.constants.MatchType;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.core.router.definition.RouterMethodDefinition;
import org.turbo.core.router.matcher.MatchResult;
import org.turbo.core.router.matcher.RouterMatcher;
import org.turbo.exception.TurboRequestExceptin;
import org.turbo.exception.TurboRouterInvokeException;
import org.turbo.exception.TurboRouterNotMatchException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认的http分发器
 */
public class DefaultHttpDispatcher implements HttpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpDispatcher.class);
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
            throw new TurboRequestExceptin("请求路径或请求方式不能为空");
        }
        MatchResult matchResult = routerMatcher.match(requestMethod, path);
        RouterMethodDefinition methodDefinition = matchResult.getDefinition();
        if (methodDefinition == null) {
            throw new TurboRouterNotMatchException("未匹配到对应的路由");
        }
        // 判断是否需要解析路径参数
        if (MatchType.PATH.equals(matchResult.getMatchType())) {
            parsePathVariable(ctx, methodDefinition);
        }
        // 获取调度器
        Object instance = routerMatcher.getInstance(methodDefinition.getControllerClass());
        // 获取方法
        Method method  = methodDefinition.getMethod();
        // 调用方法
        try {
            return method.invoke(instance, ctx);
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            log.error("方法调用失败", e);
            throw new TurboRouterInvokeException(throwable.getMessage());
        } catch (IllegalAccessException e) {
            log.error("方法调用失败", e);
            throw new TurboRouterInvokeException(e.getMessage());
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
        Map<String, String> pathVariables = ctx.getPathVariables();
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
    }
}
