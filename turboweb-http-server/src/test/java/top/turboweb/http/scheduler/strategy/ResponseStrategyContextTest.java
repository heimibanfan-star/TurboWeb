package top.turboweb.http.scheduler.strategy;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import top.turboweb.http.response.InternalCallResponse;
import top.turboweb.http.response.InternalCallResponse.InternalCallType;
import top.turboweb.http.testkit.Stubs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ResponseStrategyContext#chooseStrategy} 的"结果类型 → 策略"选择表（spec §5：每个 InternalCallType 一条）。
 * 只钉选择结果，不跑策略的写出路径——后者需要真通道，归阶段 4。
 */
class ResponseStrategyContextTest {

    /** 同时是 HttpResponse 与 InternalCallResponse：选择只依赖 getType()。 */
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

    private static ResponseStrategyContext context() {
        return new ResponseStrategyContext(false, Stubs.threadPool());
    }

    private static HttpResponse typed(InternalCallType type) {
        return new TypedResponse(type);
    }

    @Test
    void everyMappedInternalCallTypeSelectsItsOwnStrategy() {
        ResponseStrategyContext ctx = context();

        assertEquals(SseResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.SSE)).getClass());
        assertEquals(FileStreamResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.FILE_STREAM)).getClass());
        assertEquals(DefaultResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.DEFAULT)).getClass());
        assertEquals(AsyncFileResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.AIO_FILE)).getClass());
        assertEquals(ZeroCopyResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.ZERO_COPY)).getClass());
        assertEquals(ReactorResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.REACTOR)).getClass());
        assertEquals(IgnoredResponseStrategy.class, ctx.chooseStrategy(typed(InternalCallType.IGNORED)).getClass());
    }

    @Test
    void streamTypeFallsBackToDefaultStrategy() {
        // STREAM 不在注册表里，必须走 chooseStrategy 的 null 回退分支，而不是 NPE
        assertEquals(DefaultResponseStrategy.class,
                context().chooseStrategy(typed(InternalCallType.STREAM)).getClass());
    }

    @Test
    void plainHttpResponseFallsBackToDefaultStrategy() {
        HttpResponse plain = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertEquals(DefaultResponseStrategy.class, context().chooseStrategy(plain).getClass());
    }

    @Test
    void nullThreadPoolIsRejected() {
        assertThrows(NullPointerException.class, () -> new ResponseStrategyContext(false, null));
    }
}
