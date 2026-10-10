package top.turboweb.http.middleware.view;

import freemarker.template.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.turboweb.commons.exception.TurboTemplateRenderException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.core.response.ViewModel;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FreemarkerTemplateMiddleware} 的渲染、缓存与两条退化路径（spec §5）。
 * <p>
 * 模板文件用 {@code @TempDir} 造，经自建 {@link URLClassLoader} 暴露给 {@code ClassTemplateLoader}；
 * 断言缓存命中时反射读私有 {@code templateCache}，因为 Freemarker 自身也会缓存，
 * 只看“返回同一个 Template 实例”分不清是谁缓存的。
 */
@SuppressWarnings("unchecked")
class FreemarkerTemplateMiddlewareTest {

    @TempDir
    Path templateRoot;

    private FreemarkerTemplateMiddleware middleware() throws IOException {
        Path dir = Files.createDirectories(templateRoot.resolve("templates"));
        Files.writeString(dir.resolve("hello.ftl"), "<h1>Hello ${name}!</h1>", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("boom.ftl"), "${missing}", StandardCharsets.UTF_8);
        return middlewareWithRoot();
    }

    private FreemarkerTemplateMiddleware middlewareWithRoot() throws IOException {
        URLClassLoader loader = new URLClassLoader(
                new URL[]{templateRoot.toUri().toURL()},
                FreemarkerTemplateMiddleware.class.getClassLoader());
        FreemarkerTemplateMiddleware middleware = new FreemarkerTemplateMiddleware(loader);
        middleware.init(null);
        return middleware;
    }

    private static Map<String, Template> cacheOf(FreemarkerTemplateMiddleware middleware)
            throws ReflectiveOperationException {
        Field field = FreemarkerTemplateMiddleware.class.getDeclaredField("templateCache");
        field.setAccessible(true);
        return (Map<String, Template>) field.get(middleware);
    }

    private static ViewModel model(String name, String attribute, Object value) {
        ViewModel model = new ViewModel();
        model.setViewName(name);
        if (attribute != null) {
            model.addAttribute(attribute, value);
        }
        return model;
    }

    @Test
    void rendersTemplateWithAttributes() throws Exception {
        assertEquals("<h1>Hello World!</h1>", middleware().render(null, model("hello", "name", "World")));
    }

    @Test
    void templateIsWrittenToTheMiddlewareCache() throws Exception {
        FreemarkerTemplateMiddleware middleware = middleware();

        assertNotNull(middleware.loadTemplate("hello"));
        assertTrue(cacheOf(middleware).containsKey("hello.ftl"), "首次加载必须写进中间件自身的模板缓存");
    }

    @Test
    void cacheCanBeDisabled() throws Exception {
        FreemarkerTemplateMiddleware middleware = middleware();
        middleware.setOpenCache(false);

        assertNotNull(middleware.loadTemplate("hello"));
        assertTrue(cacheOf(middleware).isEmpty(), "关闭缓存时不应写进中间件自身的模板缓存");
    }

    @Test
    void missingTemplateIsWrapped() throws Exception {
        TurboTemplateRenderException thrown = assertThrows(TurboTemplateRenderException.class,
                () -> middleware().loadTemplate("missing"));

        assertEquals("模板加载失败", thrown.getMessage());
    }

    @Test
    void renderFailureIsWrapped() throws Exception {
        // boom.ftl 引用未定义变量 missing → FreeMarker 抛 InvalidReferenceException → 包装成渲染失败
        TurboTemplateRenderException thrown = assertThrows(TurboTemplateRenderException.class,
                () -> middleware().render(null, model("boom", null, null)));

        assertEquals("模板渲染失败", thrown.getMessage());
    }

    @Test
    void customSuffixIsHonored() throws Exception {
        Path dir = Files.createDirectories(templateRoot.resolve("templates"));
        Files.writeString(dir.resolve("page.html"), "ok", StandardCharsets.UTF_8);
        FreemarkerTemplateMiddleware middleware = middleware();
        middleware.setTemplateSuffix(".html");

        assertEquals("ok", middleware.render(null, model("page", null, null)));
    }

    @Test
    void customTemplatePathIsHonored() throws Exception {
        Path dir = Files.createDirectories(templateRoot.resolve("views"));
        Files.writeString(dir.resolve("v.ftl"), "view", StandardCharsets.UTF_8);

        // 产品 init() 以当时 templatePath eager 构建 ClassTemplateLoader(FreemarkerTemplateMiddleware.java:111-113、:123)，
        // 故自定义路径必须在 init 之前设置（brief 原顺序 set 在 init 后，路径不生效，红因见 STEP2-6 节）。
        URLClassLoader loader = new URLClassLoader(
                new URL[]{templateRoot.toUri().toURL()},
                FreemarkerTemplateMiddleware.class.getClassLoader());
        FreemarkerTemplateMiddleware middleware = new FreemarkerTemplateMiddleware(loader);
        middleware.setTemplatePath("views");
        middleware.init(null);

        assertEquals("view", middleware.render(null, model("v", null, null)));
    }

    @Test
    void defaultConstructorUsesModuleClasspathAndWrapsMissingTemplate() {
        FreemarkerTemplateMiddleware middleware = new FreemarkerTemplateMiddleware();
        middleware.init(null);

        TurboTemplateRenderException thrown = assertThrows(TurboTemplateRenderException.class,
                () -> middleware.loadTemplate("no-such-template-on-classpath"));
        assertEquals("模板加载失败", thrown.getMessage());
    }

    @Test
    void templateMiddlewareRendersOnlyViewModelResults() throws Exception {
        FreemarkerTemplateMiddleware middleware = middleware();
        middleware.setNext(new FixedResult(model("hello", "name", "T")));
        assertEquals("<h1>Hello T!</h1>", middleware.invoke(null), "next 返回 ViewModel 时必须渲染");

        middleware.setNext(new FixedResult("plain"));
        assertEquals("plain", middleware.invoke(null), "非 ViewModel 结果必须原样返回");
    }

    /** 固定返回值的下游中间件，用来驱动 TemplateMiddleware.invoke 的两个分支。 */
    private static final class FixedResult extends Middleware {

        private final Object value;

        FixedResult(Object value) {
            this.value = value;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            return value;
        }
    }
}
