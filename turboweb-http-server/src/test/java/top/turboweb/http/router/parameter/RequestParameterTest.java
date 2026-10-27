package top.turboweb.http.router.parameter;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RequestParameter 承载契约:路径与查询两份映射的取值语义(首值、缺参、flag 段 null)、
 * 多值出现顺序、源映射防御拷贝与视图不可变。
 */
class RequestParameterTest {

    private static RequestParameter sample() {
        Map<String, String> path = new HashMap<>();
        path.put("id", "7");
        Map<String, List<String>> query = new HashMap<>();
        query.put("tag", List.of("a", "b"));
        // List.of 禁 null:无等号段的 null 取值用 singletonList 承载
        query.put("flag", Collections.singletonList(null));
        return new RequestParameter(path, query);
    }

    @Test
    void pathReturnsValueAndMissingIsNull() {
        RequestParameter params = sample();
        assertEquals("7", params.pathParam("id"));
        assertNull(params.pathParam("nope"));
    }

    @Test
    void queryReturnsFirstValue() {
        assertEquals("a", sample().queryParam("tag"));
    }

    @Test
    void queryOfMissingNameIsNull() {
        assertNull(sample().queryParam("nope"));
    }

    @Test
    void flagSegmentWithoutEqualsHasNullFirstValue() {
        assertNull(sample().queryParam("flag"), "?flag 无等号段值为 null,首值即 null");
    }

    @Test
    void queriesReturnsAllValuesInOrder() {
        assertEquals(List.of("a", "b"), sample().queryParams("tag"), "同名多值按出现顺序保留");
    }

    @Test
    void queriesOfMissingNameReturnsEmptyList() {
        assertEquals(List.of(), sample().queryParams("nope"));
    }

    @Test
    void viewsAreUnmodifiable() {
        RequestParameter params = sample();
        assertThrows(UnsupportedOperationException.class, () -> params.pathParams().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> params.queryParams().put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> params.queryParams("tag").add("c"),
                "内层值列表同样不可变");
    }

    @Test
    void constructorCopiesSourceMaps() {
        Map<String, String> path = new HashMap<>();
        path.put("id", "1");
        RequestParameter params = new RequestParameter(path, Map.of());
        path.put("id", "2");
        assertEquals("1", params.pathParam("id"), "装配后源映射的改动不得传导进承载对象");
    }

    @Test
    void equalsComparesBothMappings() {
        assertEquals(sample(), sample());
        assertNotEquals(sample(), new RequestParameter(Map.of("id", "8"), Map.of()));
    }
}
