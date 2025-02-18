package org.turbo.core.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;
import org.turbo.exception.TurboResponseRepeatWriteException;
import org.turbo.exception.TurboSerializableException;

import java.util.HashMap;
import java.util.Map;

/**
 * http请求的上下文
 */
public class HttpContext {

    private final HttpInfoRequest request;
    private final HttpInfoResponse response;
    private final Map<String, String> pathVariables = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 是否已经写入内容
     */
    private boolean isWrite = false;

    public HttpContext(HttpInfoRequest request, HttpInfoResponse response) {
        this.request = request;
        this.response = response;
    }

    public HttpInfoRequest getRequest() {
        return request;
    }

    /**
     * 响应json数据
     *
     * @param status 响应状态
     * @param data   响应数据
     */
    public void json(HttpResponseStatus status, Object data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        try {
            response.setContent(objectMapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e.getMessage());
        }
        response.setContentType("application/json;charset=utf-8");
        isWrite = true;
    }

    public void json(Object data) {
        json(HttpResponseStatus.OK, data);
    }

    public void json(HttpResponseStatus status) {
        json(status, "");
    }

    public void text(HttpResponseStatus status, String data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        response.setContent(data);
        response.setContentType("text/plain;charset=utf-8");
        isWrite = true;
    }

    public void text(String data) {
        text(HttpResponseStatus.OK, data);
    }

    public void html(HttpResponseStatus status, String data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        response.setContent(data);
        response.setContentType("text/html;charset=utf-8");
        isWrite = true;
    }

    public void html(String data) {
        html(HttpResponseStatus.OK, data);
    }

    public boolean isWrite() {
        return isWrite;
    }

    public HttpInfoResponse getResponse() {
        return response;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }
}
