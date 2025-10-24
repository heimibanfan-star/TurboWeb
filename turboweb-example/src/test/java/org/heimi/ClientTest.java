package org.heimi;

import io.netty.handler.codec.http.*;
import top.turboweb.client.engine.HttpClientEngine;
import top.turboweb.commons.config.GlobalConfig;


public class ClientTest {
    public static void main(String[] args) {
        HttpClientEngine engine = new HttpClientEngine(config -> {
            config.ioThread(1);
            config.baseUrl("https://api.deepseek.com");
            config.timeout(1000);
        });
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/v1/chat/completions");
        request.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer " + System.getenv("deepseek"));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        String json = """
                {
                    "model": "deepseek-chat",
                    "messages": [
                        {
                            "role": "user",
                            "content": "你好吗?"
                        }
                    ],
                    "stream": false
                }
                """;
        request.content().writeBytes(json.getBytes());
        HttpResponse response = engine.send(request);
        FullHttpResponse fullHttpResponse = (FullHttpResponse) response;
        System.out.println(fullHttpResponse.content().toString(GlobalConfig.getResponseCharset()));
    }
}
