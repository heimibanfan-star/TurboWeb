package top.turboweb.http.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboExceptionHandlerException;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.DefaultStatusHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.DuplicateRuntimeHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.ExplodingCtorHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.InheritedHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.NoAnnotatedMethodHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.NoDefaultCtorHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.PackagePrivateBaseSubHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.PlainHandler;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.UserExceptionHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExceptionHandlerContainer} 的注册表语义与 {@link ExceptionHandlerContainerInitHelper}
 * 的注解扫描。
 *
 * <p>init helper 不做 classpath 扫描：只扫描显式传入对象上的公有注解方法，因此夹具都按
 * "new 出来再传进去"的方式使用。
 */
class ExceptionHandlerContainerTest {

    @Test
    @DisplayName("注册后按精确类取回同一 Definition，未注册返回 null")
    void addThenGetReturnsSameDefinitionAndUnknownReturnsNull() {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        ExceptionHandlerDefinition definition = ExceptionHandlerFixtures.definition(IllegalStateException.class);

        container.addExceptionHandler(definition);

        assertSame(definition, container.getExceptionHandlerDefinition(IllegalStateException.class));
        assertNull(container.getExceptionHandlerDefinition(IllegalArgumentException.class));
    }

    @Test
    @DisplayName("getExceptionHandlerDefinitions 暴露已注册的全部映射")
    void definitionsMapExposesRegistrations() {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        ExceptionHandlerDefinition onRuntime = ExceptionHandlerFixtures.definition(RuntimeException.class);
        ExceptionHandlerDefinition onIllegalState = ExceptionHandlerFixtures.definition(IllegalStateException.class);
        container.addExceptionHandler(onRuntime);
        container.addExceptionHandler(onIllegalState);

        Map<Class<? extends Throwable>, ExceptionHandlerDefinition> definitions =
                container.getExceptionHandlerDefinitions();

        assertEquals(2, definitions.size());
        assertSame(onRuntime, definitions.get(RuntimeException.class));
        assertSame(onIllegalState, definitions.get(IllegalStateException.class));
    }

    @Test
    @DisplayName("同一异常类型重复注册抛 TurboExceptionHandlerException，且不覆盖原有定义")
    void duplicateExceptionTypeIsRejectedWithoutOverwrite() {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        ExceptionHandlerDefinition first = ExceptionHandlerFixtures.definition(IllegalStateException.class);
        ExceptionHandlerDefinition second = ExceptionHandlerFixtures.definition(IllegalStateException.class);
        container.addExceptionHandler(first);

        TurboExceptionHandlerException ex = assertThrows(TurboExceptionHandlerException.class,
                () -> container.addExceptionHandler(second));

        assertTrue(ex.getMessage().startsWith("重复的异常处理器："), ex.getMessage());
        assertTrue(ex.getMessage().contains(IllegalStateException.class.getName()), ex.getMessage());
        assertSame(first, container.getExceptionHandlerDefinition(IllegalStateException.class),
                "被拒绝的注册不得改动原定义");
    }

    @Test
    @DisplayName("initContainer 扫描显式传入对象的公有注解方法并绑定可调用句柄")
    void initContainerRegistersAnnotatedPublicMethodsWithBoundHandles() throws Throwable {
        ExceptionHandlerContainer container =
                ExceptionHandlerContainerInitHelper.initContainer(List.of(new UserExceptionHandler()));

        ExceptionHandlerDefinition iae = container.getExceptionHandlerDefinition(IllegalArgumentException.class);
        assertNotNull(iae, "带 @ExceptionHandler 的公有方法应被注册");
        assertSame(UserExceptionHandler.class, iae.getHandlerClass());
        assertEquals(HttpResponseStatus.BAD_REQUEST, iae.getHttpResponseStatus(),
                "@ExceptionResponseStatus(400) 应落到 Definition 上");
        assertEquals("iae:boom", iae.getMethodHandler().invoke(new IllegalArgumentException("boom")),
                "句柄应已 bindTo 到处理器实例，可直接调用");

        ExceptionHandlerDefinition rte = container.getExceptionHandlerDefinition(RuntimeException.class);
        assertNotNull(rte);
        assertEquals(HttpResponseStatus.OK, rte.getHttpResponseStatus(), "无状态码注解时用默认 200");
        assertEquals("rte:boom", rte.getMethodHandler().invoke(new RuntimeException("boom")));
    }

    @Test
    @DisplayName("@ExceptionResponseStatus 不带 value 时取注解默认值 500")
    void bareStatusAnnotationUsesItsDefaultValue() {
        ExceptionHandlerContainer container =
                ExceptionHandlerContainerInitHelper.initContainer(List.of(new DefaultStatusHandler()));

        ExceptionHandlerDefinition definition = container.getExceptionHandlerDefinition(IllegalStateException.class);
        assertNotNull(definition);
        assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, definition.getHttpResponseStatus());
    }

    @Test
    @DisplayName("没有注解方法的对象不贡献任何注册")
    void objectsWithoutAnnotatedMethodsContributeNothing() {
        ExceptionHandlerContainer container = ExceptionHandlerContainerInitHelper.initContainer(
                List.of(new NoAnnotatedMethodHandler(), new UserExceptionHandler()));

        assertEquals(2, container.getExceptionHandlerDefinitions().size(),
                "只有 UserExceptionHandler 的两个注解方法入选");
    }

    @Test
    @DisplayName("继承来的公有注解方法也会被扫描，declaringClass 记在声明它的父类上")
    void inheritedAnnotatedMethodsAreScannedWithTheirDeclaringClass() throws Throwable {
        ExceptionHandlerContainer container =
                ExceptionHandlerContainerInitHelper.initContainer(List.of(new InheritedHandler()));

        ExceptionHandlerDefinition definition = container.getExceptionHandlerDefinition(NumberFormatException.class);
        assertNotNull(definition, "getMethods() 会返回继承的公有方法");
        assertSame(ExceptionHandlerFixtures.BaseAnnotatedHandler.class, definition.getHandlerClass(),
                "handlerClass 取 method.getDeclaringClass()，指向声明方法的父类");
        assertEquals("nf:boom", definition.getMethodHandler().invoke(new NumberFormatException("boom")));
    }

    @Test
    @DisplayName("两个处理器争同一异常类型时 initContainer 抛 TurboExceptionHandlerException")
    void duplicateExceptionTypeAcrossHandlersFailsFast() {
        List<Object> handlers = List.of(new UserExceptionHandler(), new DuplicateRuntimeHandler());
        assertThrows(TurboExceptionHandlerException.class,
                () -> ExceptionHandlerContainerInitHelper.initContainer(handlers));
    }

    @Test
    @DisplayName("钉住 HS-19：package-private 声明类的注解方法经合成桥以公有子类名义照常注册，IllegalAccessException catch 源码不可达")
    void annotatedMethodOnPackagePrivateBaseIsBridgedOntoPublicSubclassAndRegistered() throws Throwable {
        // 台账（brief 登记第 2 条）授权执行者裁决的补位。"public 子类 + package-private 父类
        // （与 helper 跨包）"的绕法在 GraalVM/OpenJDK 21 上确实进不了 InitHelper :109-110 的
        // IllegalAccessException catch，但成因与台账设想相反：getMethods() 只暴露编译期生成在
        // 公有子类上的合成桥方法——declaringClass=公有子类（故 unreflect 不抛错），且
        // @ExceptionHandler 连同 value 随桥复制（编译产物反射实测）。于是 handler 照常注册，
        // 只是 handlerClass 记在公有子类上而非声明它的 package-private 父类（与公有父类的
        // InheritedHandler 用例恰成对照：那里 handlerClass=父类）。:109-110 两行按结构性边界记账。
        // 钉住 HS-19：若将来 JDK/javac 改为暴露声明类本身的原方法（unreflect 抛 IllegalAccessException、
        // initContainer 整容器失败），本用例应改为期望修正后行为。
        ExceptionHandlerContainer container = ExceptionHandlerContainerInitHelper.initContainer(
                List.of(new PackagePrivateBaseSubHandler()));

        assertEquals(1, container.getExceptionHandlerDefinitions().size(),
                "注解随合成桥保留，仍被扫描注册");
        ExceptionHandlerDefinition definition =
                container.getExceptionHandlerDefinition(UnsupportedOperationException.class);
        assertNotNull(definition);
        assertSame(PackagePrivateBaseSubHandler.class, definition.getHandlerClass(),
                "handlerClass 取桥方法的 getDeclaringClass()：公有子类，而非 package-private 父类");
        assertEquals("unsupported:boom", definition.getMethodHandler().invoke(new UnsupportedOperationException("boom")),
                "桥方法经 unreflect+bindTo 后可直接调用");
    }

    // ------------------------------------------------------------------ createInstance

    @Test
    @DisplayName("createInstance（私有工厂，生产路径无调用者）能创建公有无参构造的实例")
    void createInstanceBuildsNoArgInstance() throws Throwable {
        assertInstanceOf(PlainHandler.class, createInstanceOf(PlainHandler.class));
    }

    @Test
    @DisplayName("createInstance：缺少无参构造时包装为 TurboExceptionHandlerException")
    void createInstanceRejectsClassWithoutNoArgConstructor() {
        TurboExceptionHandlerException ex = assertThrows(TurboExceptionHandlerException.class,
                () -> createInstanceOf(NoDefaultCtorHandler.class));
        assertTrue(ex.getMessage().contains("NoDefaultCtorHandler"), ex.getMessage());
    }

    @Test
    @DisplayName("钉住 HS-18：构造执行失败时包装为 TurboExceptionHandlerException，cause 与消息双双丢失")
    void createInstanceWrapsConstructorFailureWithoutPreservingCause() {
        // 构造抛出的 IllegalStateException 被 Constructor.newInstance（:73）包进
        // InvocationTargetException，而 catch 分支用 new TurboExceptionHandlerException(e.getMessage())。
        // 实测（GraalVM/OpenJDK 21）：InvocationTargetException.getMessage() 为 null（它的 message
        // 不参与 toString 合成，底层异常只挂在 getCause() 上），单参 String 构造又不带 cause——
        // 包装后消息与原因双双丢失，只剩一条空消息的 TurboExceptionHandlerException（台账 HS-18 原样成立）。
        // 钉住 HS-18：若将来修正包装方式（改用带 cause 的构造并传入底层异常消息），本用例应改为期望正确行为
        // （cause 为 IllegalStateException、消息非空）。
        TurboExceptionHandlerException ex = assertThrows(TurboExceptionHandlerException.class,
                () -> createInstanceOf(ExplodingCtorHandler.class));
        assertNull(ex.getCause(), "当前行为：底层构造异常没有被保留为 cause");
        assertNull(ex.getMessage(), "当前行为：InvocationTargetException.getMessage() 为 null，包装消息同为空");
    }

    /** createInstance 是私有方法，只能反射触达；把 InvocationTargetException 拆开便于断言真实异常类型。 */
    private static Object createInstanceOf(Class<?> clazz) throws Throwable {
        Method method = ExceptionHandlerContainerInitHelper.class.getDeclaredMethod("createInstance", Class.class);
        method.setAccessible(true);
        try {
            return method.invoke(null, clazz);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
