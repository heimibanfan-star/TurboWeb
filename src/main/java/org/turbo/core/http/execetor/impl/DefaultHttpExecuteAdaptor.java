package org.turbo.core.http.execetor.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.constants.FontColors;
import org.turbo.core.http.context.HttpContext;
import org.turbo.core.http.execetor.HttpDispatcher;
import org.turbo.core.http.execetor.HttpExecuteAdaptor;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.exception.TurboSerializableException;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        long startTime = System.currentTimeMillis();
        HttpInfoRequest httpInfoRequest = HttpInfoRequestPackageUtils.packageRequest(request);
        try {
            // 创建响应对象
            HttpInfoResponse response = new HttpInfoResponse(request.protocolVersion(), HttpResponseStatus.OK);
            HttpContext context = new HttpContext(httpInfoRequest, response);
            Object result =  httpDispatcher.dispatch(context);
            // 判断是否写入内容
            if (context.isWrite()) {
                return response;
            }
            if (result == null) {
                response.setStatus(HttpResponseStatus.NO_CONTENT);
                return response;
            }
            if (result instanceof String) {
                response.setContent((String) result);
            } else {
                try {
                    response.setContentType(objectMapper.writeValueAsString(result));
                } catch (JsonProcessingException e) {
                    log.error("序列化失败:", e);
                    throw new TurboSerializableException(e.getMessage());
                }
            }
            return response;
        } catch (Throwable e) {
            throw e;
        } finally {
            long endTime = System.currentTimeMillis();
            log(httpInfoRequest, endTime - startTime);
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
