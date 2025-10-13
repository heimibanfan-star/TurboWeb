package org.heimi;

import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;

/**
 * TODO
 */
public class TestApplication {
    public static void main(String[] args) throws InterruptedException {
        TurboHttpClient http = new DefaultTurboHttpClient("http://www.baidu.com");
        String html = http.get("/").as(String.class);
        System.out.println(html);
    }
}
