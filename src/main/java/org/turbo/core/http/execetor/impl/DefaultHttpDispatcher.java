package org.turbo.core.http.execetor.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.core.router.matcher.RouterMatcher;

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
        // TODO 执行分发操作
        HttpInfoResponse response = new HttpInfoResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        response.setContent("<h1>hello world</h1>");
        response.setContentType("text/html");
        return response;
    }
}
