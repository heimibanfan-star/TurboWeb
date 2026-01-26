package org.heimi;


import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.result.ClientResult;

public class ClientTest {

    public static void main(String[] args) {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        ClientResult clientResult = httpClient.get("https://www.baidu.com");
        System.out.println(clientResult.as(String.class));
    }
}
