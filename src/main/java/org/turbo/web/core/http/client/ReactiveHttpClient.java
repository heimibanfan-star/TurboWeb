package org.turbo.web.core.http.client;

import reactor.netty.http.client.HttpClient;

/**
 * 反应式http客户端
 */
public class ReactiveHttpClient {

    private final HttpClient httpClient;

    public ReactiveHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
