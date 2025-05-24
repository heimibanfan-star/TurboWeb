package org.turboweb.client.result;

import io.netty.handler.codec.http.HttpHeaders;

/**
 * rest风格的返回结果
 */
public class RestResponseResult<T> {

    private final HttpHeaders headers;
    private final T body;

    public RestResponseResult(HttpHeaders headers, T body) {
        this.headers = headers;
        this.body = body;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public T getBody() {
        return body;
    }
}
