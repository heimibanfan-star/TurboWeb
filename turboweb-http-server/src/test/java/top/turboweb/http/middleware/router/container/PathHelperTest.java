package top.turboweb.http.middleware.router.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link PathHelper#mergePath} 的路径规整边界（spec §5：middleware/router 容器与注册）。
 *
 * <p>合并规则是注解路由拼接类前缀与方法路径的唯一入口
 * （{@code AnnoRouterManager.initRouterDefinition:270-283} 对 @Get/@Post/… 全部调它），
 * 每条断言各对应源码里的一个分支。
 */
class PathHelperTest {

    @Test
    void nullPreAndNullPathCollapseToRootSlash() {
        assertEquals("/", PathHelper.mergePath(null, null));
    }

    @Test
    void blankPathsCollapseToRootSlash() {
        assertEquals("/", PathHelper.mergePath("", ""));
        assertEquals("/", PathHelper.mergePath("/", "/"));
        assertEquals("/", PathHelper.mergePath(null, "/"));
    }

    @Test
    void prefixWithoutLeadingSlashGetsOne() {
        assertEquals("/api/users", PathHelper.mergePath("api", "users"));
    }

    @Test
    void prefixTrailingSlashIsTrimmed() {
        assertEquals("/api/users", PathHelper.mergePath("/api/", "/users"));
    }

    @Test
    void childWithoutLeadingSlashGetsOne() {
        assertEquals("/api/users", PathHelper.mergePath("/api", "users"));
    }

    @Test
    void blankOrRootChildReturnsPrefixVerbatim() {
        assertEquals("/api", PathHelper.mergePath("/api", ""));
        assertEquals("/api", PathHelper.mergePath("/api", "/"));
        assertEquals("/api", PathHelper.mergePath("/api/", ""));
    }

    @Test
    void blankPrefixWithRealChildKeepsSingleLeadingSlash() {
        assertEquals("/users", PathHelper.mergePath(null, "/users"));
        assertEquals("/users", PathHelper.mergePath("/", "users"));
    }

    @Test
    void redundantSlashesAreCollapsed() {
        assertEquals("/api/users", PathHelper.mergePath("/api//", "//users//"));
    }

    @Test
    void trailingSlashIsStrippedFromResult() {
        assertEquals("/api/users", PathHelper.mergePath("/api", "/users/"));
    }

    @Test
    void starInMergedPathIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PathHelper.mergePath("/api", "/x*"));
        assertEquals("路由路径不允许包含*或..", ex.getMessage());
    }

    @Test
    void dotDotInMergedPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PathHelper.mergePath("/api", "/../x"));
    }

    @Test
    void questionMarkIsNotRejectedHere() {
        // '?' 不归 mergePath 管：AnnoRouterManager.doInitRouterDefinition:295 另行校验并抛
        // TurboRouterDefinitionCreateException。这里只钉住 mergePath 自己的边界。
        assertEquals("/api/x?", PathHelper.mergePath("/api", "/x?"));
    }

    @Test
    void blankChildSkipsWildcardValidation() {
        // path 为 null/""/"/" 时提前返回（:29-31），'*'/'..' 的校验被跳过；
        // 含 '*' 的前缀由调用方的二次校验兜底，mergePath 自身不查。
        assertEquals("/a*", PathHelper.mergePath("/a*", "/"));
    }
}
