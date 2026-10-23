package top.turboweb.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.server.runtime.HttpServerRuntime;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.http.testkit.Servers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L3 探针：真实服务器 + 真实 HTTP 请求，一次 GET 走完 codec -> aggregator -> 调度器
 * -> 处理器链 -> 转换器 -> 响应策略 的主干，并验证应用关闭会沿资源作用域销毁中间件链。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeIT {

    /** 服务器在 worker/虚拟线程里调用 invoke，事件列表必须线程安全 */
    private final List<String> events = new CopyOnWriteArrayList<>();
    private Servers servers;
    private HttpClient client;

    @BeforeAll
    void startServer() {
        Middleware hello = new RecordingMiddleware("hello", events, "hello-turbo");
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .middleware(hello)
                .build();
        servers = Servers.start(runtime);
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    void stopServer() {
        if (servers != null) {
            servers.close();
        }
    }

    @Test
    @DisplayName("GET 返回中间件载荷，Content-Type为text/html")
    void getReturnsMiddlewarePayload() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(servers.baseUrl() + "/smoke"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("hello-turbo", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("")
                        .startsWith("text/html"),
                "字符串载荷按 text/html 返回，实际: " + response.headers().map());
        assertTrue(events.contains("invoke:hello"), "请求必须穿过中间件链，事件: " + events);
    }

    @Test
    @DisplayName("服务器关闭沿资源作用域销毁中间件链，不断言端口与线程状态")
    void closeReleasesMiddlewareChain() throws Exception {
        List<String> secondEvents = new CopyOnWriteArrayList<>();
        Middleware second = new RecordingMiddleware("second", secondEvents, "second-payload");
        HttpServerRuntime runtime = new HttpServerRuntime.Builder()
                .middleware(second)
                .build();

        try (Servers local = Servers.start(runtime)) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(local.baseUrl() + "/second"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals("second-payload", response.body());
        }
        // 关闭后中间件的 destroy 必须被调用（由 HttpServerRuntime#close 经 ScopedResource 触发）
        assertTrue(secondEvents.contains("destroy:second"),
                "应用关闭必须销毁中间件链，事件: " + secondEvents);
        // 注意：此处刻意不断言"端口拒绝连接"也不sleep等待——Netty静默期异步，见 spec §7.3-7.4
    }
}
