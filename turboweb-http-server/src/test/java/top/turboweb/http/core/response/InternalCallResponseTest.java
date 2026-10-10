package top.turboweb.http.core.response;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link InternalCallResponse} 的枚举契约与"结果类型 → {@code InternalCallType}"事实（spec §5：response）。
 *
 * <p>这里只固定映射事实：{@code IgnoredHttpResponse → IGNORED}、未实现本接口的结果一律按
 * {@code DEFAULT} 兜底（`HttpResult`/`HttpInfoResponse`/`ViewModel`/内存模式的 `HttpFileResult`）；
 * `HttpFileResult` 超限产出 `FileStreamResponse → FILE_STREAM` 的断言在 `HttpFileResultTest`。
 * 真正的策略选择（`ResponseStrategyContext.chooseStrategy`）归 Task 16。
 */
class InternalCallResponseTest {

    @Test
    void enumExposesExactlyTheEightDocumentedTypes() {
        Set<String> names = Arrays.stream(InternalCallResponse.InternalCallType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of("ZERO_COPY", "FILE_STREAM", "AIO_FILE", "SSE",
                        "DEFAULT", "REACTOR", "STREAM", "IGNORED"),
                names, "新增/删除 InternalCallType 必须同步更新策略注册表，枚举契约要能被用例抓住");
    }

    @Test
    void ignoredResponseIsSingletonWithIgnoredType() {
        IgnoredHttpResponse first = IgnoredHttpResponse.ignore();
        assertSame(first, IgnoredHttpResponse.ignore(), "忽略响应是单例");
        assertEquals(InternalCallResponse.InternalCallType.IGNORED, first.getType());
        assertEquals(HttpResponseStatus.OK, first.status());
        assertEquals(HttpVersion.HTTP_1_1, first.protocolVersion());
    }

    @Test
    void plainResultTypesAreNotInternalCallResponses() {
        HttpInfoResponse info = new HttpInfoResponse(HttpResponseStatus.OK);
        FullHttpResponse fileBody = (FullHttpResponse) HttpFileResult.bytes(new byte[]{1}).createResponse();
        try {
            // brief 原文 `new HttpResult<>()` 无零参构造可用（HttpResult.java:28-44），编译修复为显式状态码构造
            assertFalse(new HttpResult<Object>(200) instanceof InternalCallResponse);
            assertFalse(info instanceof InternalCallResponse);
            assertFalse(new ViewModel() instanceof InternalCallResponse);
            assertFalse(fileBody instanceof InternalCallResponse,
                    "内存模式的 HttpFileResult 产出普通 FullHttpResponse，走 DEFAULT 兜底");
        } finally {
            info.release();
            fileBody.release();
        }
    }
}
