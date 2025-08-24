package org.heimi;

import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.engine.HttpClientEngine;

/**
 * TODO
 */
public class ClientApplication {

    private static final TurboHttpClient httpClient;

    static {
        String baseUrl = "http://127.0.0.1:8080";
        httpClient = new DefaultTurboHttpClient(new HttpClientEngine(baseUrl));
    }


    public static void main(String[] args) {
        var result =  httpClient.get("/hello", config -> {
            config.query(p -> {
                p.add("name", "turbo");
            });
        });
        User user = result.data(User.class);
        System.out.println(user);
    }
}
