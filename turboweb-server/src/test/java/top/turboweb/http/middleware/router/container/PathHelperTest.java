package top.turboweb.http.middleware.router.container;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PathHelper#mergePath} 的路径规整与非法路径边界。
 *
 * <p>合并规则是注解路由拼接前缀与路径的唯一入口
 * （{@code AnnoRouterManager.initRouterDefinition} 对 @Get/@Post/… 两两调它,
 * {@code RouteHandlerMetaInfoHelper.parse} 的三级合并也走它,一次传入多段）。
 *
 * <p>本类的职责边界:把入参拼成标准路径——以 / 开头、结尾不含 /、段间至多一个斜杠。
 * 缺前导斜杠补齐、单个尾斜杠裁除属规整;出现连续斜杠属非法路径直接拒绝,不再折叠;
 * 各级入参都提不出路径段时退化成根路径,根路径不受支持同样直接拒绝,
 * 判定落在整条合并结果上而非两两合并的中间结果上。问号与通配符等路由语法字符的校验不归本类
 * （通配符由 {@code AnnoRouterManager.doInitRouterDefinition} 拒绝）,每条断言各对应源码里的一个分支。
 */
class PathHelperTest {

    private static final String ROOT_MESSAGE_PREFIX = "非法路径，不支持根路径路由: ";

    @Test
    void noSegmentLeftIsIllegalRootPath() {
        IllegalArgumentException bothNull = assertThrows(IllegalArgumentException.class,
                () -> PathHelper.mergePath(null, null), "两侧都提不出路径段即根路径,不受支持");
        assertEquals(ROOT_MESSAGE_PREFIX + "[null, null]", bothNull.getMessage(), "异常带各级入参原文");
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("", ""));
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/", "/"));
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath(null, "/"), "null 与斜杠同义,合起来仍是根");
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/", null));
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/", ""));
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath());
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("", "", "/"),
                "多段合并同样只看最终结果,三段全空退化成根");
    }

    @Test
    void oneSegmentAnywhereKeepsResultOutOfRoot() {
        assertEquals("/api", PathHelper.mergePath("/api", ""), "子路径为空而前缀有段,结果是前缀");
        assertEquals("/api", PathHelper.mergePath("/api", "/"));
        assertEquals("/api", PathHelper.mergePath("/api/", null));
        assertEquals("/api", PathHelper.mergePath(null, null, "/api"), "多段里只要有一段就成立");
        assertEquals("/users", PathHelper.mergePath("/", "users"));
        assertEquals("/users", PathHelper.mergePath("", "users"));
    }

    @Test
    void prefixWithoutLeadingSlashGetsOne() {
        assertEquals("/api/users", PathHelper.mergePath("api", "users"));
    }

    @Test
    void prefixTrailingSlashIsTrimmed() {
        assertEquals("/api/users", PathHelper.mergePath("/api/", "/users"), "单尾斜杠入参合法，裁剪后拼接缝只剩一个斜杠");
    }

    @Test
    void childWithoutLeadingSlashGetsOne() {
        assertEquals("/api/users", PathHelper.mergePath("/api", "users"));
    }

    @Test
    void severalSegmentsMergeInOrder() {
        assertEquals("/api/hello/1", PathHelper.mergePath("api", "hello", "1"), "多段按入参顺序拼接");
        assertEquals("/api/hello/1", PathHelper.mergePath("/api", "/hello/", "/1/"), "各段各自补前导裁尾");
        assertEquals("/hello/1", PathHelper.mergePath("", "/hello", "/1"), "空前缀不占位");
        assertEquals("/g", PathHelper.mergePath("", "", "/g"), "类路径缺省而方法路径有段,不属根路径");
    }

    @Test
    void trailingSlashIsStrippedFromResult() {
        assertEquals("/api/users", PathHelper.mergePath("/api", "/users/"));
    }

    @Test
    void bothSidesTrailingSlashLeaveSingleSlashAtSeam() {
        assertEquals("/a/b", PathHelper.mergePath("/a/", "/b/"), "两侧各带单尾斜杠时拼接缝只有一个斜杠");
    }

    @Test
    void resultsNeverEndWithSlash() {
        List<String> results = List.of(
                PathHelper.mergePath("a", "b"),
                PathHelper.mergePath("a/", "b/"),
                PathHelper.mergePath("/a/", "b"),
                PathHelper.mergePath("", "b"),
                PathHelper.mergePath("a", ""),
                PathHelper.mergePath("a", "", "b"));
        for (String result : results) {
            assertTrue(result.startsWith("/"), "结果以斜杠开头:" + result);
            assertFalse(result.endsWith("/"), "结果结尾不含斜杠:" + result);
        }
    }

    @Test
    void consecutiveSlashesInPrefixIsIllegalPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PathHelper.mergePath("/api//", "users"));
        assertEquals("非法路径，不允许出现连续的“/”: /api//", ex.getMessage(), "连续斜杠拒绝而非折叠，异常带非法入参原文");
    }

    @Test
    void consecutiveSlashesInChildIsIllegalPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PathHelper.mergePath("/api", "//users"));
        assertEquals("非法路径，不允许出现连续的“/”: //users", ex.getMessage(), "子路径一侧同样拒绝");
    }

    @Test
    void leadingDoubleSlashIsIllegalPath() {
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("//api", "users"));
    }

    @Test
    void trailingDoubleSlashIsIllegalPath() {
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/api", "users//"));
    }

    @Test
    void consecutiveSlashesRejectedEvenWhenOtherSideIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/api//", ""),
                "空子路径不再使前缀的非法斜杠绕过校验");
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/", "/x//"));
    }

    @Test
    void consecutiveSlashesRejectedEvenWhenResultWouldBeRoot() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PathHelper.mergePath("//", "//"));
        assertEquals("非法路径，不允许出现连续的“/”: //", ex.getMessage(),
                "逐段结构校验先于根路径判定,两种非法各报各的");
    }

    @Test
    void wildcardCharactersAreNotRejectedHere() {
        assertEquals("/api/x*", PathHelper.mergePath("/api", "/x*"), "通配符校验已移出本类，由路由语法校验方拒绝");
        assertEquals("/a*", PathHelper.mergePath("/a*", "/"), "前缀含通配符也原样返回");
        assertEquals("/api/../x", PathHelper.mergePath("/api", "/../x"), "相对路径片段不属本类的校验范围");
    }

    @Test
    void questionMarkIsNotRejectedHere() {
        assertEquals("/api/x?", PathHelper.mergePath("/api", "/x?"),
                "问号由调用方另行校验并抛 TurboRouterDefinitionCreateException，本类只规整斜杠");
    }

    @Test
    void whitespaceInsideSegmentIsPreserved() {
        assertEquals("/a b/c d", PathHelper.mergePath("/a b", "/c d"), "不做裁剪、转义与编码，只处理斜杠");
    }

    @Test
    void helperIsAPrivateConstructorUtility() throws Exception {
        var constructor = PathHelper.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()), "工具类构造器私有");
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance(), "构造器可为覆盖率测试实例化");
    }
}
