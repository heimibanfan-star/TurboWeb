package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.VoidRequestMessage;
import top.turboweb.http.orchestrator.parser.FormContentParser;
import top.turboweb.http.orchestrator.parser.JsonContentParser;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.request.JsonRequestMessage;
import top.turboweb.http.testkit.Requests;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.RequestLine;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RequestOrchestrator} 的消息装配口径：请求行构造期快照、
 * 缺省字符集透传、请求头拷贝为消息自有的可写存储——装配后与请求头集合不再联动；
 * 消息类按内容类型分派，内置内容配强类型消息、无内容装配无体消息。
 */
class MessageAssemblyTest {

    /** 不经头与地址通道的用例统一带入的请求体内容 */
    private static final Object NO_CONTENT = new Object();

    private static HttpRequestMessage<?> assemble(FullHttpRequest request, Charset charset) {
        return RequestOrchestrator.assembleMessage(request, NO_CONTENT, charset);
    }

    // ---------- 请求行与地址快照 ----------

    @Test
    void methodIsSnapshottedFromRequestLine() {
        FullHttpRequest get = Requests.get("/x");
        FullHttpRequest post = Requests.postJson("/x", "{}");
        try {
            HttpRequestMessage<?> getMessage = assemble(get, StandardCharsets.UTF_8);
            HttpRequestMessage<?> postMessage = assemble(post, StandardCharsets.UTF_8);
            assertEquals(HttpRequestMethod.GET, getMessage.method(), "请求行方法字面量转为标准方法");
            assertEquals(HttpRequestMethod.POST, postMessage.method());
        } finally {
            Requests.release(get);
            Requests.release(post);
        }
    }

    @Test
    void requestLineIsSnapshottedWithMethodAndTarget() {
        FullHttpRequest req = Requests.get("/x?y=1");
        try {
            HttpRequestMessage<?> message = assemble(req, StandardCharsets.UTF_8);

            RequestLine line = message.requestLine();

            assertEquals(HttpRequestMethod.GET, line.method(), "请求行承载请求方式");
            assertEquals("/x?y=1", line.target().raw(), "请求行承载请求目标原文");
            assertEquals("/x", line.target().path(), "请求行承载剥离查询参数后的路径");
            assertSame(line, message.requestLine(), "构造期快照，重复访问同实例");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nonStandardMethodIsRejectedAtAssembly() {
        FullHttpRequest req = Requests.raw(HttpMethod.valueOf("PROPFIND"), "/x", null, null,
                StandardCharsets.UTF_8);
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> assemble(req, StandardCharsets.UTF_8),
                    "扩展方法不在标准方法范围内，装配期即拒");
            assertTrue(ex.getMessage().contains("PROPFIND"));
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 请求头拷贝 ----------

    @Test
    void headersAreSnapshotCopiedIntoOwnStorage() {
        FullHttpRequest req = Requests.get("/x");
        try {
            req.headers().set("Host", "localhost");
            req.headers().add("X-Multi", "a");
            req.headers().add("X-Multi", "b");
            HttpRequestMessage<?> message = assemble(req, StandardCharsets.UTF_8);
            assertTrue(message.containsHeader("host"), "装配时请求头拷入消息自有存储");
            assertEquals(List.of("a", "b"), message.headerValues("X-Multi"));

            req.headers().set("X-Late", "v");
            assertFalse(message.containsHeader("X-Late"), "装配后请求头集合变动不再联动消息");

            message.setHeader("X-Added", "w");
            message.removeHeader("Host");
            assertNull(req.headers().get("X-Added"), "消息写通道落在自有存储，不回写请求头集合");
            assertNotNull(req.headers().get("Host"), "消息头删除不回写请求头集合");
            assertFalse(message.containsHeader("Host"), "消息头删除只作用于自有存储");
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 请求目标 ----------

    @Test
    void targetCarriesRawPathAndQueryParams() {
        FullHttpRequest req = Requests.get("/api/search?q=hello&tag=a&tag=b");
        try {
            HttpRequestMessage<?> message = assemble(req, StandardCharsets.UTF_8);
            assertEquals("/api/search?q=hello&tag=a&tag=b", message.requestLine().target().raw(), "请求目标承载地址原文");
            assertEquals("/api/search", message.requestLine().target().path(), "请求目标承载剥离查询参数后的路径");
            assertEquals(List.of("hello"), message.requestLine().target().queryParams().get("q"));
            assertEquals(List.of("a", "b"), message.requestLine().target().queryParams().get("tag"));
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void queryCharsetPlumbedToTargetDecoding() {
        FullHttpRequest req = Requests.get("/x?n=%D6%D0");
        try {
            HttpRequestMessage<?> message = assemble(req, Charset.forName("GBK"));
            assertEquals("中", message.requestLine().target().queryParams().get("n").get(0),
                    "装配期缺省字符集透传至请求目标的查询解码");
        } finally {
            Requests.release(req);
        }
    }

    // ---------- 消息类分派 ----------

    @Test
    void contentIsTheInjectedInstance() {
        FullHttpRequest req = Requests.get("/x");
        try {
            Object content = new Object();
            HttpRequestMessage<?> message = RequestOrchestrator.assembleMessage(req, content, StandardCharsets.UTF_8);
            assertSame(content, message.content(), "内容由装配方带入，消息不另建");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void builtinContentDispatchesToTypedMessages() throws Exception {
        FullHttpRequest json = Requests.postJson("/x", "{}");
        FullHttpRequest form = Requests.postForm("/x", "a=1");
        try {
            Object jsonData = new JsonContentParser().parse(json, StandardCharsets.UTF_8);
            assertInstanceOf(JsonRequestMessage.class,
                    RequestOrchestrator.assembleMessage(json, jsonData, StandardCharsets.UTF_8),
                    "内置 JSON 内容装配为 JSON 请求消息");
            Object formData = new FormContentParser().parse(form, StandardCharsets.UTF_8);
            assertInstanceOf(FormRequestMessage.class,
                    RequestOrchestrator.assembleMessage(form, formData, StandardCharsets.UTF_8),
                    "内置表单内容装配为表单请求消息");
        } finally {
            Requests.release(json);
            Requests.release(form);
        }
    }

    @Test
    void nullContentAssemblesVoidMessage() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpRequestMessage<?> message = RequestOrchestrator.assembleMessage(req, null, StandardCharsets.UTF_8);
            assertInstanceOf(VoidRequestMessage.class, message, "无内容装配为无体消息");
            assertNull(message.content(), "无体消息不携带内容");
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void accessorsReturnStableInstances() {
        FullHttpRequest req = Requests.get("/x");
        try {
            HttpRequestMessage<?> message = assemble(req, StandardCharsets.UTF_8);
            assertSame(message.requestLine().target(), message.requestLine().target(), "请求目标构造期建立，重复访问同实例");
            assertSame(message.content(), message.content());
        } finally {
            Requests.release(req);
        }
    }

    @Test
    void nullRequestIsRejected() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> RequestOrchestrator.assembleMessage(null, NO_CONTENT, StandardCharsets.UTF_8));
        assertEquals("request can not be null", ex.getMessage());
    }

    @Test
    void nullQueryCharsetIsRejected() {
        FullHttpRequest req = Requests.get("/x");
        try {
            NullPointerException ex = assertThrows(NullPointerException.class,
                    () -> RequestOrchestrator.assembleMessage(req, NO_CONTENT, null));
            assertEquals("charset must not be null", ex.getMessage());
        } finally {
            Requests.release(req);
        }
    }
}
