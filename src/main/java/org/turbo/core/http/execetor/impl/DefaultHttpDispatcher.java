package org.turbo.core.http.execetor.impl;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;

/**
 * 默认的http分发器
 */
public class DefaultHttpDispatcher implements HttpDispatcher {

    @Override
    public HttpInfoResponse dispatch(HttpInfoRequest request) {
        HttpInfoResponse response = new HttpInfoResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        // TODO 执行分发操作
        response.setContent("<h1>hello world</h1>");
        response.setContentType("text/html");
        return response;
    }
}
