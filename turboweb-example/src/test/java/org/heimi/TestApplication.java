package org.heimi;

import reactor.netty.http.HttpProtocol;
import reactor.netty.tcp.SslProvider;
import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.engine.HttpClientEngine;

import java.net.http.HttpClient;

/**
 * TODO
 */
public class TestApplication {
    public static void main(String[] args) throws InterruptedException {
        HttpClientEngine engine = new HttpClientEngine(config -> {
            config.protocol(HttpProtocol.HTTP11);
            config.ssl(ssl -> ssl.sslContext(SslProvider.defaultClientProvider().getSslContext()));
        });
        TurboHttpClient httpClient = new DefaultTurboHttpClient(engine);
        String html = httpClient.get("https://www.baidu.com").as(String.class);
        System.out.println(html);
        engine.close();
    }
}
