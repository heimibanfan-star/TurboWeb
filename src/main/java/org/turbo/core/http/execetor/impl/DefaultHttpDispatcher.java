package org.turbo.core.http.execetor.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.core.router.definition.RouterMethodDefinition;
import org.turbo.core.router.matcher.RouterMatcher;
import org.turbo.exception.TurboRequestExceptin;
import org.turbo.exception.TurboRouterNotMatchException;

import java.util.Objects;

/**
 * 默认的http分发器
 */
public class DefaultHttpDispatcher implements HttpDispatcher {

    private final RouterMatcher routerMatcher;

    public DefaultHttpDispatcher(RouterMatcher routerMatcher) {
        this.routerMatcher = routerMatcher;
    }

    @Override
    public HttpInfoResponse dispatch(HttpInfoRequest request) {
        // 获取请求方式
        String method = request.getMethod();
        // 获取请求路径
        String path = request.getUri();
        if (Objects.isNull(method) || Objects.isNull(path) || method.isEmpty() || path.isEmpty()) {
            throw new TurboRequestExceptin("请求路径或请求方式不能为空");
        }
        RouterMethodDefinition methodDefinition = routerMatcher.match(method, path);
        if (methodDefinition == null) {
            throw new TurboRouterNotMatchException("未匹配到对应的路由");
        }
        // TODO 执行分发操作
        HttpInfoResponse response = new HttpInfoResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        response.setContent("<h1>hello world</h1>");
        response.setContentType("text/html");
        return response;
    }
}
