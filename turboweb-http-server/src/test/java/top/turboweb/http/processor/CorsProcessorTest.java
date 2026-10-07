package top.turboweb.http.processor;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.connect.ConnectSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CorsProcessor} 的预检短路、实际请求追加与配置项取值（spec §5：processor）。
 *
 * <p>OPTIONS 场景不挂下游处理器——若产品把 OPTIONS 也交给 {@code next()}，末端返回 null 会让
 * {@code handleCorsHeaders} NPE，用例随之红；非 OPTIONS 场景挂一个固定响应的下游处理器，
 * 断言同实例返回且头被追加。全部纯内存。
 */
class CorsProcessorTest {

    /** 固定响应的下游处理器：非 OPTIONS 路径下代替真实的 MiddlewareInvokeProcessor。 */
    private static Processor returning(final HttpResponse response) {
        return new Processor() {
            @Override
            public HttpResponse invoke(FullHttpRequest request, ConnectSession connectSession) {
                return response;
            }
        };
    }

    private static FullHttpRequest preflight(String origin) {
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/x");
        if (origin != null) {
            request.headers().set("Origin", origin);
        }
        return request;
    }

    private static FullHttpRequest get(String origin) {
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/x");
        if (origin != null) {
            request.headers().set("Origin", origin);
        }
        return request;
    }

    @Test
    @DisplayName("OPTIONS 预检：直接返回 200 空响应并带上默认 CORS 头，不调用下游")
    void preflightReturnsEmptyResponseWithDefaultCorsHeaders() {
        CorsProcessor processor = new CorsProcessor(); // 故意不挂 next

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(preflight("http://a.com"), null);

        assertEquals(HttpResponseStatus.OK, response.status());
        assertEquals(0, response.content().readableBytes());
        assertEquals("http://a.com", response.headers().get("Access-Control-Allow-Origin"));
        assertEquals("GET,POST,PUT,DELETE", response.headers().get("Access-Control-Allow-Methods"));
        assertEquals("*", response.headers().get("Access-Control-Allow-Headers"));
        assertEquals("Content-Disposition", response.headers().get("Access-Control-Expose-Headers"));
        assertEquals("3600", response.headers().get("Access-Control-Max-Age"));
    }

    @Test
    @DisplayName("非 OPTIONS：先取下游响应再加 CORS 头，返回同一实例")
    void actualRequestDecoratesDownstreamResponse() {
        FullHttpResponse downstream =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        CorsProcessor processor = new CorsProcessor();
        processor.setNextProcessor(returning(downstream));

        HttpResponse response = processor.invoke(get("http://a.com"), null);

        assertSame(downstream, response);
        assertEquals("http://a.com", response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("缺少 Origin 时按字面量 \"null\" 处理")
    void missingOriginBecomesNullLiteral() {
        CorsProcessor processor = new CorsProcessor();

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(preflight(null), null);

        assertEquals("null", response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("白名单外的 origin 不写任何 CORS 头（allowedOrigins 未命中分支）")
    void originOutsideAllowListGetsNoCorsHeaders() {
        CorsProcessor processor = new CorsProcessor();
        processor.getConfig().setAllowedOrigins(List.of("http://allowed.com"));

        FullHttpResponse denied =
                (FullHttpResponse) processor.invoke(preflight("http://other.com"), null);
        assertFalse(denied.headers().contains("Access-Control-Allow-Origin"));

        FullHttpResponse allowed =
                (FullHttpResponse) processor.invoke(preflight("http://allowed.com"), null);
        assertEquals("http://allowed.com",
                allowed.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("allowCredentials 且 Origin 为 * ：提前 return，两个头都不写")
    void allowCredentialsWithWildcardOriginSkipsAllHeaders() {
        CorsProcessor processor = new CorsProcessor();
        processor.getConfig().setAllowCredentials(true);

        FullHttpResponse response = (FullHttpResponse) processor.invoke(preflight("*"), null);

        assertFalse(response.headers().contains("Access-Control-Allow-Credentials"));
        assertFalse(response.headers().contains("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("allowCredentials 且具体 Origin：写 Allow-Credentials=true 与具体 Allow-Origin")
    void allowCredentialsWithConcreteOriginSetsCredentialHeader() {
        CorsProcessor processor = new CorsProcessor();
        processor.getConfig().setAllowCredentials(true);

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(preflight("http://a.com"), null);

        assertEquals("true", response.headers().get("Access-Control-Allow-Credentials"));
        assertEquals("http://a.com", response.headers().get("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("方法/头/暴露头配置为 * 时原样输出 *")
    void wildcardConfigsAreEmittedLiterally() {
        CorsProcessor processor = new CorsProcessor();
        processor.getConfig().setAllowedMethods(List.of("*"));
        processor.getConfig().setAllowedHeaders(List.of("*"));
        processor.getConfig().setExposedHeaders(List.of("*"));

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(preflight("http://a.com"), null);

        assertEquals("*", response.headers().get("Access-Control-Allow-Methods"));
        assertEquals("*", response.headers().get("Access-Control-Allow-Headers"));
        assertEquals("*", response.headers().get("Access-Control-Expose-Headers"));
    }

    @Test
    @DisplayName("方法/头/暴露头为具体列表时逗号拼接，maxAge 用配置值")
    void concreteConfigsAreJoinedWithCommas() {
        CorsProcessor processor = new CorsProcessor();
        processor.getConfig().setAllowedMethods(List.of("GET", "PATCH"));
        processor.getConfig().setAllowedHeaders(List.of("X-A", "X-B"));
        processor.getConfig().setExposedHeaders(List.of("X-C"));
        processor.getConfig().setMaxAge(60);

        FullHttpResponse response =
                (FullHttpResponse) processor.invoke(preflight("http://a.com"), null);

        assertEquals("GET,PATCH", response.headers().get("Access-Control-Allow-Methods"));
        assertEquals("X-A,X-B", response.headers().get("Access-Control-Allow-Headers"));
        assertEquals("X-C", response.headers().get("Access-Control-Expose-Headers"));
        assertEquals("60", response.headers().get("Access-Control-Max-Age"));
    }
}
