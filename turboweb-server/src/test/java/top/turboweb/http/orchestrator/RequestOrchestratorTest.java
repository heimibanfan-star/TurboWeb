package top.turboweb.http.orchestrator;

import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.response.InternalCallResponse;
import top.turboweb.http.core.response.InternalCallResponse.InternalCallType;
import top.turboweb.http.processor.Processor;
import top.turboweb.http.testkit.Stubs;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RequestOrchestrator} 的行为锚。
 * <p>
 * 编排器承载原 {@code VirtualThreadHttpScheduler} 中非调度部分的逻辑：执行处理器链、
 * 按响应类型选择策略写出、无论成败均释放请求；写出完成凭据（ChannelFuture）返回给调用方，
 * 由调用方（调度器）挂接请求日志。本组用例逐面钉住该契约，语义与原调度器同路径保持一致。
 */
class RequestOrchestratorTest {

    /** 返回固定响应的链节点，模拟业务处理链的正常产出。 */
    private static final class ReturningProcessor extends Processor {

        private final HttpResponse response;

        ReturningProcessor(HttpResponse response) {
            this.response = response;
        }

        @Override
        public HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
            return response;
        }
    }

    /** 执行即抛出的链节点，模拟链上异常穿透。 */
    private static final class ThrowingProcessor extends Processor {

        @Override
        public HttpResponse invoke(FullHttpRequest fullHttpRequest, ConnectSession connectSession) {
            throw new RuntimeException("boom");
        }
    }

    /** 带内部调用类型的响应，用于驱动策略选择（IGNORED 面）。 */
    private static final class TypedResponse extends DefaultFullHttpResponse implements InternalCallResponse {

        private final InternalCallType type;

        TypedResponse(InternalCallType type) {
            super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            this.type = type;
        }

        @Override
        public InternalCallType getType() {
            return type;
        }
    }

    private static FullHttpRequest newRequest() {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
    }

    private static HttpResponse okResponse(String body) {
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.content().writeCharSequence(body, StandardCharsets.UTF_8);
        return response;
    }

    @Test
    void orchestrateWritesChainResponseAndReleasesRequest() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ConnectSession session = new InternalConnectSession(channel);
        RequestOrchestrator orchestrator =
                new RequestOrchestrator(new ReturningProcessor(okResponse("pong")), Stubs.threadPool(), false);
        FullHttpRequest request = newRequest();

        ChannelFuture future = orchestrator.orchestrate(request, session);

        assertNotNull(future, "默认策略必须返回写出凭据");
        assertTrue(future.isSuccess(), "EmbeddedChannel 写出应即时成功");
        FullHttpResponse outbound = channel.readOutbound();
        assertEquals(HttpResponseStatus.OK, outbound.status());
        assertEquals("pong", outbound.content().toString(StandardCharsets.UTF_8));
        assertEquals(0, request.refCnt(), "编排完成后请求必须被释放");
        channel.finishAndReleaseAll();
    }

    @Test
    void orchestrateReleasesRequestWhenChainThrows() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ConnectSession session = new InternalConnectSession(channel);
        RequestOrchestrator orchestrator =
                new RequestOrchestrator(new ThrowingProcessor(), Stubs.threadPool(), false);
        FullHttpRequest request = newRequest();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> orchestrator.orchestrate(request, session));
        assertEquals("boom", thrown.getMessage());
        assertEquals(0, request.refCnt(), "链上抛出异常时请求同样必须被释放");
        assertNull(channel.readOutbound(), "异常路径不得写出任何响应");
        channel.finishAndReleaseAll();
    }

    @Test
    void writeResponseReturnsNullForIgnoredResponseType() {
        EmbeddedChannel channel = new EmbeddedChannel();
        ConnectSession session = new InternalConnectSession(channel);
        RequestOrchestrator orchestrator =
                new RequestOrchestrator(new ReturningProcessor(null), Stubs.threadPool(), false);

        ChannelFuture future = orchestrator.writeResponse(session, new TypedResponse(InternalCallType.IGNORED));

        assertNull(future, "忽略策略无写出凭据，调用方据此跳过日志");
        assertNull(channel.readOutbound(), "忽略策略不得写出任何响应");
        channel.finishAndReleaseAll();
    }

    @Test
    void constructorRejectsNullThreadPool() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> new RequestOrchestrator(new ReturningProcessor(null), null, false));
        assertEquals("磁盘IO线程池不能为null", thrown.getMessage());
    }
}
