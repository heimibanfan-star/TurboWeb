package org.turbo.core.http.request;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 封装http请求信息
 */
public class HttpInfoRequest {

    private final Logger log = LoggerFactory.getLogger(HttpInfoRequest.class);


    private final FullHttpRequest request;
    private final Map<String, List<String>> queryParams;
    private final HttpContent content;
    private final String contentType;

    public HttpInfoRequest(FullHttpRequest request, Map<String, List<String>> queryParams, HttpContent content) {
        this.request = request;
        this.queryParams = queryParams;
        this.content = content;
        if (content != null) {
            this.contentType = content.getContentType();
        } else {
            this.contentType = null;
        }
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public HttpContent getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public HttpVersion getProtocolVersion() {
        return request.protocolVersion();
    }
}
