package top.turboweb.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.StatusLine;
import top.turboweb.serializer.JsonSerializer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RestHttpClient 的语义与边界用例：以伪造引擎钉住请求装配口径，
 * 以本机 HTTP 对端做端到端验证。
 */
class RestHttpClientTest {

    /**
     * 捕获引擎：记录最近一次收到的请求消息，返回固定响应，不发起真实传输。
     */
    private static final class CapturingEngine implements HttpClientEngine {

        HttpRequestMessage<byte[]> lastRequest;

        @Override
        public ByteResponseMessage request(HttpRequestMessage<byte[]> request) {
            this.lastRequest = request;
            return new ByteResponseMessage(StatusLine.OK, "fake-body".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public StreamHandle stream(HttpRequestMessage<byte[]> request, StreamHandler handler) {
            throw new UnsupportedOperationException("捕获引擎不支持流式请求");
        }

        @Override
        public void close() {
        }
    }

    @AfterEach
    void tearDown() {
        for (AutoCloseable resource : closeables) {
            try {
                resource.close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("资源关闭失败", e);
            }
        }
        closeables.clear();
    }

    private final List<AutoCloseable> closeables = new ArrayList<>();

    /**
     * 建立随用例关闭的传输引擎。
     */
    private ReactiveHttpClientEngine newEngine() {
        ReactiveHttpClientEngine engine = new ReactiveHttpClientEngine(ReactiveClientConfig.builder().build());
        closeables.add(engine);
        return engine;
    }

    /**
     * 建立随用例关闭的本机 HTTP 对端。
     */
    private HttpServer newServer(String path, Map<String, String> seen) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext(path, exchange -> {
            try {
                String lengthHeader = exchange.getRequestHeaders().getFirst("Content-Length");
                byte[] body = lengthHeader != null && Integer.parseInt(lengthHeader) > 0
                        ? exchange.getRequestBody().readAllBytes()
                        : new byte[0];
                seen.put("method", exchange.getRequestMethod());
                seen.put("content-type", exchange.getRequestHeaders().getFirst("Content-Type"));
                seen.put("body", new String(body, StandardCharsets.UTF_8));
                byte[] resp = "echo-ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                exchange.getResponseBody().write(resp);
                exchange.close();
            } catch (Throwable t) {
                seen.put("handler-error", String.valueOf(t));
            }
        });
        server.start();
        closeables.add(() -> server.stop(0));
        return server;
    }

    @Test
    @DisplayName("通用入口装配请求方式与目标原文")
    void requestBuildsMethodAndTarget() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);

        client.request("http://example.com/api", HttpRequestMethod.OPTIONS);

        assertNotNull(engine.lastRequest);
        assertEquals(HttpRequestMethod.OPTIONS, engine.lastRequest.method());
        assertEquals("http://example.com/api", engine.lastRequest.requestLine().target().raw());
    }

    @Test
    @DisplayName("null 请求体发空体且不带 Content-Type")
    void nullDataSendsEmptyBodyWithoutContentType() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);

        client.post("http://example.com/api");

        assertNotNull(engine.lastRequest);
        assertEquals(0, engine.lastRequest.content().length);
        assertFalse(engine.lastRequest.headers().contains("Content-Type"));
    }

    @Test
    @DisplayName("对象请求体 JSON 序列化并自动补 application/json")
    void objectDataJsonSerializedWithContentType() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);

        client.post("http://example.com/api", Map.of("name", "turbo"));

        assertNotNull(engine.lastRequest);
        assertEquals("application/json", engine.lastRequest.headers().get("Content-Type"));
        assertTrue(new String(engine.lastRequest.content(), StandardCharsets.UTF_8).contains("\"name\":\"turbo\""));
    }

    @Test
    @DisplayName("字节数组请求体原文发送且不改写 Content-Type")
    void byteArrayDataRawWithoutContentType() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);
        byte[] raw = {1, 2, 3};

        client.put("http://example.com/api", raw);

        assertNotNull(engine.lastRequest);
        assertArrayEquals(raw, engine.lastRequest.content());
        assertFalse(engine.lastRequest.headers().contains("Content-Type"));
    }

    @Test
    @DisplayName("baseUrl 去尾部斜杠后与路径拼接一次")
    void baseUrlJoinedWithNormalizedSlashes() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine, "http://example.com/api/");

        client.get("/users");

        assertNotNull(engine.lastRequest);
        assertEquals("http://example.com/api/users", engine.lastRequest.requestLine().target().raw());
    }

    @Test
    @DisplayName("路径缺前导斜杠时补齐拼接")
    void baseUrlSuppliesLeadingSlash() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine, "http://example.com/api");

        client.get("users");

        assertNotNull(engine.lastRequest);
        assertEquals("http://example.com/api/users", engine.lastRequest.requestLine().target().raw());
    }

    @Test
    @DisplayName("无 baseUrl 时路径原样透传由引擎校验")
    void noBaseUrlPassesThrough() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);

        client.get("http://example.com/users");

        assertNotNull(engine.lastRequest);
        assertEquals("http://example.com/users", engine.lastRequest.requestLine().target().raw());
    }

    @Test
    @DisplayName("空串 baseUrl 与 null 同口径按无 baseUrl 处理")
    void emptyBaseUrlTreatedAsAbsent() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine, "");

        client.get("http://example.com/users");

        assertNotNull(engine.lastRequest);
        assertEquals("http://example.com/users", engine.lastRequest.requestLine().target().raw());
    }

    @Test
    @DisplayName("五族方法重载各装配对应请求方式")
    void methodOverloadsRouteToRightMethod() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);
        String url = "http://example.com/api";

        client.get(url);
        assertEquals(HttpRequestMethod.GET, engine.lastRequest.method());
        client.get(url, "body");
        assertEquals(HttpRequestMethod.GET, engine.lastRequest.method());
        client.post(url);
        assertEquals(HttpRequestMethod.POST, engine.lastRequest.method());
        client.post(url, "body");
        assertEquals(HttpRequestMethod.POST, engine.lastRequest.method());
        client.put(url);
        assertEquals(HttpRequestMethod.PUT, engine.lastRequest.method());
        client.put(url, "body");
        assertEquals(HttpRequestMethod.PUT, engine.lastRequest.method());
        client.delete(url);
        assertEquals(HttpRequestMethod.DELETE, engine.lastRequest.method());
        client.delete(url, "body");
        assertEquals(HttpRequestMethod.DELETE, engine.lastRequest.method());
        client.patch(url);
        assertEquals(HttpRequestMethod.PATCH, engine.lastRequest.method());
        client.patch(url, "body");
        assertEquals(HttpRequestMethod.PATCH, engine.lastRequest.method());
    }

    @Test
    @DisplayName("自定义序列化器生效")
    void customSerializerUsed() {
        CapturingEngine engine = new CapturingEngine();
        JsonSerializer serializer = new JsonSerializer() {
            @Override
            public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<?, ?> beanToMap(Object bean) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String beanToJson(Object bean) {
                return "custom-" + bean;
            }

            @Override
            public <T> T jsonToBean(String json, Class<T> beanClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<?, ?> jsonToMap(String json) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> List<T> jsonToList(String json, Class<T> beanClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T deepinClone(T bean) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T jsonUpdateBean(String json, T bean) {
                throw new UnsupportedOperationException();
            }
        };
        RestHttpClient client = new RestHttpClient(engine, null, serializer);

        client.post("http://example.com/api", "payload");

        assertNotNull(engine.lastRequest);
        assertEquals("custom-payload", new String(engine.lastRequest.content(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("构造校验：引擎与序列化器为空拒绝")
    void constructorRejectsNullEngineAndSerializer() {
        assertThrows(NullPointerException.class, () -> new RestHttpClient(null));
        CapturingEngine engine = new CapturingEngine();
        assertThrows(NullPointerException.class, () -> new RestHttpClient(engine, null, null));
    }

    @Test
    @DisplayName("path 为 null 拒绝")
    void nullPathRejected() {
        RestHttpClient client = new RestHttpClient(new CapturingEngine());
        assertThrows(NullPointerException.class, () -> client.get(null));
    }

    @Test
    @DisplayName("端到端：POST JSON 请求体与 Content-Type 到达服务端，响应体透出")
    void postJsonEndToEnd() throws IOException {
        Map<String, String> seen = Collections.synchronizedMap(new HashMap<>());
        HttpServer server = newServer("/echo", seen);
        int port = server.getAddress().getPort();
        RestHttpClient client = new RestHttpClient(newEngine(), "http://127.0.0.1:" + port);

        ByteResponseMessage response = client.post("/echo", Map.of("name", "turbo"));

        assertEquals("POST", seen.get("method"));
        assertEquals("application/json", seen.get("content-type"));
        assertTrue(seen.get("body").contains("\"name\":\"turbo\""));
        assertEquals(StatusLine.OK.code(), response.statusLine().code());
        assertEquals("echo-ok", new String(response.content(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("端到端：GET 无请求体不带 Content-Type")
    void getWithoutBodyEndToEnd() throws IOException {
        Map<String, String> seen = Collections.synchronizedMap(new HashMap<>());
        HttpServer server = newServer("/echo", seen);
        int port = server.getAddress().getPort();
        RestHttpClient client = new RestHttpClient(newEngine(), "http://127.0.0.1:" + port);

        ByteResponseMessage response = client.get("/echo");

        assertEquals("GET", seen.get("method"));
        assertNull(seen.get("content-type"));
        assertEquals("", seen.get("body"));
        assertEquals(StatusLine.OK.code(), response.statusLine().code());
    }

    @Test
    @DisplayName("响应内容透出引擎返回值")
    void responsePassedThroughFromEngine() {
        CapturingEngine engine = new CapturingEngine();
        RestHttpClient client = new RestHttpClient(engine);

        ByteResponseMessage response = client.get("http://example.com/api");

        assertEquals("fake-body", new String(response.content(), StandardCharsets.UTF_8));
        assertEquals(StatusLine.OK.code(), response.statusLine().code());
    }
}
