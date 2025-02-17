package org.turbo.core.http.execetor.impl;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.constants.FontColors;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.utils.http.HttpInfoRequestPackageUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认http 处理适配器
 */
public class DefaultHttpExecuteAdaptor implements HttpExecuteAdaptor {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpExecuteAdaptor.class);
    private final HttpDispatcher httpDispatcher;
    private final Map<String, String> colors = new ConcurrentHashMap<>(4);

    {
        colors.put("GET", FontColors.GREEN);
        colors.put("POST", FontColors.YELLOW);
        colors.put("PUT", FontColors.BLUE);
        colors.put("DELETE", FontColors.RED);
    }

    public DefaultHttpExecuteAdaptor(HttpDispatcher httpDispatcher) {
        this.httpDispatcher = httpDispatcher;
    }

    @Override
    public HttpInfoResponse doExecutor(FullHttpRequest request) {
        HttpInfoRequest httpInfoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
        try {
            long startTime = System.currentTimeMillis();
            HttpInfoResponse response =  httpDispatcher.dispatch(httpInfoRequest);
            long endTime = System.currentTimeMillis();
            log(httpInfoRequest, endTime - startTime);
            return response;
        } catch (Exception e) {
            // TODO 进行异常处理器的匹配
            log.error("执行请求失败", e);
            return new HttpInfoResponse(httpInfoRequest.getProtocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void log(HttpInfoRequest request, long ms) {
        String method = request.getRequest().method().name();
        if (!colors.containsKey(method)) {
            return;
        }
        String color = colors.get(method);
        String uri = request.getRequest().uri();
        if (ms > 0) {
            System.out.println(color + "%s  %s  耗时:%sms".formatted(method, uri, ms));
        } else {
            System.out.println(color + "%s  %s  耗时: <1ms".formatted(method, uri));
        }
        System.out.print(FontColors.BLACK);
    }
}
