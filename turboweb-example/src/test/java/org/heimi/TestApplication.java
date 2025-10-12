package org.heimi;

import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.engine.HttpClientEngine;
import top.turboweb.client.result.ClientResult;

/**
 * TODO
 */
public class TestApplication {
    public static void main(String[] args) throws InterruptedException {
        TurboHttpClient httpClient = new DefaultTurboHttpClient(new HttpClientEngine("http://localhost:8080"));
        for (int i = 0; i < 10; i++) {
            ClientResult result = httpClient.get("/order" +  i);
            System.out.println(result);
        }
    }
}
