package org.example.httpclient;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContextBuilder;
import top.turboweb.client.DefaultTurboHttpClient;
import top.turboweb.client.TurboHttpClient;
import top.turboweb.client.converter.JsonConverter;
import top.turboweb.client.engine.HttpClientEngine;

import javax.net.ssl.SSLException;
import java.io.File;

public class HttpClientApplication {

    public static void main(String[] args) {
//        test1();
//        test2();
//        test3();
//        test4();
//        test5();
//        test6();
//        test7();
//        test8();
//        test9();
//        test10();
//        test11();
        test12();
    }

    // GET /hello
    public static void test1() {
        // 使用默认配置创建一个客户端
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        String res = httpClient.get("http://localhost:8080/hello").as(String.class);
        System.out.println(res);
    }

    ///  GET /user
    public static void test2() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        User user = httpClient.get("http://localhost:8080/user").as(User.class);
        System.out.println(user);
    }

    public static void test3() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        User user = httpClient.get("http://localhost:8080/query", config -> {
                    config.query(params -> {
                        params.add("name", "Tom");
                        params.add("age", String.valueOf(18));
                    });
                })
                .as(User.class);
        System.out.println(user);
    }

    public static void test4() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        User user = new User("Tom", 18);
        User user1 = httpClient.post("http://localhost:8080/json", config -> {
                    config.data(user);
                })
                .as(User.class);
        System.out.println(user1);
    }

    public static void test5() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        User user = httpClient.post("http://localhost:8080/form", config -> {
            config.form(
                    params -> {
                        params.add("name", "Tom");
                        params.add("age", "18");
                    }
            );
        }).as(User.class);
        System.out.println(user);
    }

    public static void test6() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        String res = httpClient.get("http://localhost:8080/interceptor", config -> {
                    config.headers(
                            headers -> {
                                headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer 123456");
                            }
                    );
                })
                .as(String.class);
        System.out.println(res);
    }

    public static void test7() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        byte[] bytes = httpClient.get("http://localhost:8080/hello").as(byte[].class);
        System.out.println(new String(bytes));
    }

    public static void test8() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient();
        // 注册请求拦截器
        httpClient.addRequestInterceptor(request -> {
            request.headers().add(HttpHeaderNames.AUTHORIZATION, "Bearer 123456");
            return request;
        });
        // 注册响应拦截器
        httpClient.addResponseInterceptor(response -> {
            // 进行响应的判断,例如状态码
            System.out.println(response.status());
            return response;
        });
        // 发送请求
        String token = httpClient.get("http://localhost:8080/interceptor").as(String.class);
        System.out.println(token);
    }

    public static void test9() {
        TurboHttpClient httpClient = new DefaultTurboHttpClient("http://localhost:8080");
        String string = httpClient.get("/hello").as(String.class);
        System.out.println(string);
    }

    public static void test10() {
        HttpClientEngine engine = new HttpClientEngine(1, "http://localhost:8080");
        TurboHttpClient httpClient = new DefaultTurboHttpClient(engine);
        String string = httpClient.get("/hello").as(String.class);
        System.out.println(string);
    }

    public static void test11() {
        HttpClientEngine engine = new HttpClientEngine(1, "http://localhost:8080");
        TurboHttpClient httpClient = new DefaultTurboHttpClient(engine, new JsonConverter());
        User user = httpClient.get("/user").as(User.class);
        System.out.println(user);
    }

    public static void test12() {

        // 证书
        File cert = new File("E://temp/server.cert");

        HttpClientEngine engine = new HttpClientEngine(config -> {
            // 设置底层IO驱动的线程为1
            config.ioThread(1);
            // 设置基础URL
            config.baseUrl("https://localhost:8080");
            // 设置客户端名称
            config.name("testHttpClient");
            // 设置超时时间
            config.timeout(5000);
            // 连接信息的配置
            config.connect(builder -> {
                // 详细参考reactor-netty
            });
            // 配置ssl，一般不需要配置，默认使用jdk的ssl，除非配置一些自签证书
            config.ssl(spec -> {
                try {
                    spec.sslContext(SslContextBuilder.forClient().trustManager(cert).build());
                } catch (SSLException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        TurboHttpClient httpClient = new DefaultTurboHttpClient(engine);
        String string = httpClient.get("/hello").as(String.class);
        System.out.println(string);
    }
}
