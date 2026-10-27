package top.turboweb.http.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpCorsConfig} 的行为锚：建造者产出不可变 CORS 配置的契约——
 * 空建造者产物逐字段等于旧线默认值、配置生效且后写覆盖前写、
 * 产物列表不可修改且免疫外部列表事后变更（防御拷贝）、null 列表与 null 元素拒收、
 * 通配判定与精确 origin 匹配（null 来源返回 false 不抛）。
 */
class HttpCorsConfigTest {

    @Test
    void emptyBuilderMatchesLegacyDefaults() {
        HttpCorsConfig config = HttpCorsConfig.builder().build();

        assertEquals(List.of("*"), config.getAllowedOrigins(), "默认允许所有来源");
        assertEquals(List.of("GET", "POST", "PUT", "DELETE"), config.getAllowedMethods());
        assertEquals(List.of("*"), config.getAllowedHeaders());
        assertEquals(List.of("Content-Disposition"), config.getExposedHeaders());
        assertFalse(config.isAllowCredentials(), "默认不允许携带凭证");
        assertEquals(3600, config.getMaxAge(), "预检缓存默认 3600 秒");
    }

    @Test
    void builderSettersApplyAndLastWriteWins() {
        HttpCorsConfig config = HttpCorsConfig.builder()
                .allowedOrigins(List.of("https://a.com", "https://b.com"))
                .allowedOrigins(List.of("https://c.com"))
                .allowedMethods(List.of("GET"))
                .allowedHeaders(List.of("X-Token"))
                .exposedHeaders(List.of("X-Total"))
                .allowCredentials(true)
                .maxAge(600)
                .build();

        assertEquals(List.of("https://c.com"), config.getAllowedOrigins(), "同一属性重复配置以最后一次为准");
        assertEquals(List.of("GET"), config.getAllowedMethods());
        assertEquals(List.of("X-Token"), config.getAllowedHeaders());
        assertEquals(List.of("X-Total"), config.getExposedHeaders());
        assertTrue(config.isAllowCredentials());
        assertEquals(600, config.getMaxAge());
    }

    @Test
    void productListIsUnmodifiable() {
        HttpCorsConfig config = HttpCorsConfig.builder().build();

        assertThrows(UnsupportedOperationException.class, () -> config.getAllowedOrigins().add("https://evil.com"));
        assertThrows(UnsupportedOperationException.class, () -> config.getAllowedMethods().add("PATCH"));
        assertThrows(UnsupportedOperationException.class, () -> config.getAllowedHeaders().add("X-Evil"));
        assertThrows(UnsupportedOperationException.class, () -> config.getExposedHeaders().add("X-Evil"));
    }

    @Test
    void externalListMutationAfterBuildDoesNotAffectConfig() {
        List<String> origins = new ArrayList<>();
        origins.add("https://a.com");

        HttpCorsConfig config = HttpCorsConfig.builder().allowedOrigins(origins).build();

        origins.add("https://evil.com");

        assertEquals(List.of("https://a.com"), config.getAllowedOrigins(), "产物与外部可变列表隔离");
    }

    @Test
    void nullListAndNullElementRejected() {
        HttpCorsConfig.Builder builder = HttpCorsConfig.builder();

        assertThrows(NullPointerException.class, () -> builder.allowedOrigins(null));
        assertThrows(NullPointerException.class, () -> builder.allowedMethods(null));
        assertThrows(NullPointerException.class, () -> builder.allowedHeaders(null));
        assertThrows(NullPointerException.class, () -> builder.exposedHeaders(null));
        assertThrows(NullPointerException.class, () -> builder.allowedOrigins(Arrays.asList("https://a.com", null)),
                "null 元素随不可变拷贝一并拒收");
    }

    @Test
    void wildcardAndExactOriginMatching() {
        HttpCorsConfig any = HttpCorsConfig.builder().build();

        assertTrue(any.isAnyOrigin(), "默认列表含通配来源");
        assertTrue(any.isAnyHeader(), "默认列表含通配请求头");
        assertFalse(any.isAnyMethod(), "默认方法为精确四个、不含通配");
        assertTrue(any.isAllowedOrigin("https://anything.com"), "通配下任意来源通过");
        assertFalse(any.isAllowedOrigin(null), "null 来源返回 false 不抛");

        HttpCorsConfig exact = HttpCorsConfig.builder()
                .allowedOrigins(List.of("https://a.com"))
                .allowedMethods(List.of("*"))
                .build();

        assertFalse(exact.isAnyOrigin());
        assertTrue(exact.isAllowedOrigin("https://a.com"));
        assertFalse(exact.isAllowedOrigin("https://b.com"));
        assertTrue(exact.isAnyMethod(), "显式配置通配方法");
    }
}
