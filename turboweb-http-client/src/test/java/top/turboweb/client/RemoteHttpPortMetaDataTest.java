package top.turboweb.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.ByteRequestMessage;
import top.turboweb.http.core.Headers;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 远程端口元数据的用例：定型属性建造期一次赋值，
 * message 按扩展头填充请求头后回调构建请求，外部后续修改不渗入。
 */
class RemoteHttpPortMetaDataTest {

    /**
     * 建立一条可作回调返回值的字节请求消息。
     */
    private static ByteRequestMessage newRequest() {
        return new ByteRequestMessage(
                new RequestLine(HttpRequestMethod.GET, new RequestTarget("http://example.com/order")),
                new byte[0]);
    }

    /**
     * 建立携带一条扩展头的映射。
     */
    private static Map<String, String[]> extendHeaders() {
        Map<String, String[]> headers = new LinkedHashMap<>();
        headers.put("X-Trace-Id", new String[]{"a1", "a2"});
        return headers;
    }

    @Test
    @DisplayName("定型属性按构造值读出")
    void propertiesExposeConstructorValues() {
        RemoteHttpPortMetaData.RequestCallback callback = (headers, args) -> null;
        RemoteHttpPortMetaData metaData = new RemoteHttpPortMetaData(
                "order/detail", extendHeaders(), HttpRequestMethod.POST, callback);

        assertEquals("order/detail", metaData.target());
        assertEquals(HttpRequestMethod.POST, metaData.method());
    }

    @Test
    @DisplayName("message 填充扩展头后回调构建请求并透传返回值")
    void messageFillsExtendHeadersAndDelegatesToCallback() {
        ByteRequestMessage request = newRequest();
        Object[] args = {"a", "b"};

        RemoteHttpPortMetaData metaData = new RemoteHttpPortMetaData(
                "order/detail", extendHeaders(), HttpRequestMethod.POST,
                (headers, cbArgs) -> {
                    assertArrayEquals(new String[]{"a1", "a2"}, headers.values("X-Trace-Id").toArray());
                    assertSame(args, cbArgs);
                    return request;
                });

        ByteRequestMessage returned = metaData.message(args);

        assertNotNull(returned);
        assertSame(request, returned);
    }

    @Test
    @DisplayName("构造后源映射突变不渗入 message 填充的请求头")
    void messageHeadersIsolatedFromSourceMutations() {
        Map<String, String[]> source = extendHeaders();
        Headers[] seen = new Headers[1];
        RemoteHttpPortMetaData metaData = new RemoteHttpPortMetaData(
                "order/detail", source, HttpRequestMethod.GET,
                (headers, args) -> {
                    seen[0] = headers;
                    return newRequest();
                });

        source.put("X-Late", new String[]{"late"});
        source.get("X-Trace-Id")[0] = "mutated";

        metaData.message(new Object[0]);
        assertArrayEquals(new String[]{"a1", "a2"}, seen[0].values("X-Trace-Id").toArray());
        assertFalse(seen[0].contains("X-Late"));
    }
}
