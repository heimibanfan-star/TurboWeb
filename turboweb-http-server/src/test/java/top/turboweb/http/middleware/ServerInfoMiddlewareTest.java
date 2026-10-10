package top.turboweb.http.middleware;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.response.HttpResult;
import top.turboweb.http.testkit.RecordingMiddleware;
import top.turboweb.http.testkit.Stubs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ServerInfoMiddleware} 的 memory / thread / gc / 未知 四分支（spec §5）。
 * <p>
 * 只断言结构（字段集合、排序、退化行为），不依赖 JVM 指标的具体数值；
 * 动态数据源换成本地 JVM 的真实 MXBean，靠“字段存在/有序”把产品代码的取值逻辑钉住。
 * {@code HttpResult} 没有 getter，用反射读私有 {@code data} 字段。
 */
@SuppressWarnings("unchecked")
class ServerInfoMiddlewareTest {

    private static HttpContext ctx(String uri, String type) {
        ServerInfoMiddleware.QueryCon query = new ServerInfoMiddleware.QueryCon();
        query.setType(type);
        return Stubs.httpContext(
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri), query);
    }

    private static Map<String, Object> dataOf(HttpResult<?> result) throws ReflectiveOperationException {
        Field data = HttpResult.class.getDeclaredField("data");
        data.setAccessible(true);
        return (Map<String, Object>) data.get(result);
    }

    @Test
    void nonMatchingPathDelegatesToNext() {
        List<String> events = new ArrayList<>();
        ServerInfoMiddleware middleware = new ServerInfoMiddleware();
        middleware.setNext(new RecordingMiddleware("down", events, "ok"));

        assertEquals("ok", middleware.invoke(ctx("/other?type=memory", "memory")));
        assertEquals(List.of("invoke:down"), events);
    }

    @Test
    void memoryBranchExposesHeapNonHeapAndNioSections() throws Exception {
        Map<String, Object> data = dataOf((HttpResult<?>) new ServerInfoMiddleware()
                .invoke(ctx("/turboWeb/serverInfo?type=memory", "memory")));

        assertEquals("success", data.get("code"));
        assertEquals("memory", data.get("type"));
        Map<String, Object> info = (Map<String, Object>) data.get("info");
        assertEquals(Set.of("heap", "nonHeap", "nio"), info.keySet());

        List<Map<String, Object>> heap = (List<Map<String, Object>>) info.get("heap");
        assertFalse(heap.isEmpty(), "JVM 至少暴露一个 heap 内存池");
        assertEquals(Set.of("name", "used", "max", "committed"), heap.get(0).keySet());

        List<Map<String, Object>> nonHeap = (List<Map<String, Object>>) info.get("nonHeap");
        assertFalse(nonHeap.isEmpty(), "JVM 至少暴露一个 non-heap 内存池");
        assertEquals(Set.of("name", "used", "max", "committed"), nonHeap.get(0).keySet());

        List<Map<String, Object>> nio = (List<Map<String, Object>>) info.get("nio");
        assertFalse(nio.isEmpty(), "JVM 至少暴露一个 NIO BufferPool");
        assertEquals(Set.of("name", "used", "capacity"), nio.get(0).keySet());
    }

    @Test
    void threadBranchIsSortedByNameAndHasStableShape() throws Exception {
        Map<String, Object> data = dataOf((HttpResult<?>) new ServerInfoMiddleware()
                .invoke(ctx("/turboWeb/serverInfo?type=thread", "thread")));

        assertEquals("success", data.get("code"));
        assertEquals("thread", data.get("type"));
        Map<String, Object> info = (Map<String, Object>) data.get("info");
        assertEquals(Set.of("stateCount", "infos"), info.keySet());

        Map<?, ?> stateCount = (Map<?, ?>) info.get("stateCount");
        assertFalse(stateCount.isEmpty());

        List<Map<String, Object>> infos = (List<Map<String, Object>>) info.get("infos");
        assertFalse(infos.isEmpty());
        assertEquals(Set.of("name", "id", "state", "blockedTime", "waitedTime", "lockName"),
                infos.get(0).keySet());
        for (int i = 1; i < infos.size(); i++) {
            String prev = (String) infos.get(i - 1).get("name");
            String cur = (String) infos.get(i).get("name");
            assertTrue(prev.compareTo(cur) <= 0, () -> "线程 info 未按 name 升序: " + prev + " > " + cur);
        }
    }

    @Test
    void gcBranchListsCollectors() throws Exception {
        Map<String, Object> data = dataOf((HttpResult<?>) new ServerInfoMiddleware()
                .invoke(ctx("/turboWeb/serverInfo?type=gc", "gc")));

        assertEquals("success", data.get("code"));
        assertEquals("gc", data.get("type"));
        List<Map<String, Object>> info = (List<Map<String, Object>>) data.get("info");
        assertFalse(info.isEmpty(), "JVM 至少暴露一个 GarbageCollectorMXBean");
        for (Map<String, Object> collector : info) {
            assertEquals(Set.of("name", "count", "time"), collector.keySet());
        }
    }

    @Test
    void unknownTypeReturnsErrorPayload() throws Exception {
        Map<String, Object> data = dataOf((HttpResult<?>) new ServerInfoMiddleware()
                .invoke(ctx("/turboWeb/serverInfo?type=uptime", "uptime")));

        assertEquals("error", data.get("code"));
        assertEquals("type不支持", data.get("message"));
        assertEquals(Set.of("code", "message"), data.keySet());
    }

    @Test
    void customRequestPathIsHonored() throws Exception {
        ServerInfoMiddleware middleware = new ServerInfoMiddleware();
        middleware.setRequestPath("/custom");

        Map<String, Object> data = dataOf((HttpResult<?>)
                middleware.invoke(ctx("/custom?type=gc", "gc")));

        assertEquals("gc", data.get("type"));
    }
}
