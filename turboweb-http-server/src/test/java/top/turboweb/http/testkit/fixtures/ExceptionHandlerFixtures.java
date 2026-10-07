package top.turboweb.http.testkit.fixtures;

import top.turboweb.anno.ExceptionHandler;
import top.turboweb.anno.ExceptionResponseStatus;
import top.turboweb.http.handler.ExceptionHandlerDefinition;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * 异常处理器夹具：异常层次 + 带注解的处理器 + 构造助手。
 *
 * <p>异常匹配这条链不做 classpath 扫描（{@code initContainer} 只扫描显式传入对象上的
 * 注解方法），所以这些注解夹具不会污染任何"注册了几个处理器"的计数断言——一律由用例
 * 显式 new 出来再传入。
 *
 * <p>处理器类通常写成 {@code public}：{@code initContainer} 里的
 * {@code MethodHandles.lookup()} 出自 {@code top.turboweb.http.handler} 包。注解方法即便
 * 声明在 package-private 基类上（{@link PackagePrivateAnnotatedBase}），{@code getMethods()}
 * 也只以公有子类的合成桥方法暴露它——注解随桥复制、declaringClass 是公有子类，
 * {@code unreflect} 实测不抛 {@code IllegalAccessException}——后果由 HS-19 用例钉住。
 */
public final class ExceptionHandlerFixtures {

    private ExceptionHandlerFixtures() {
    }

    // ------------------------------------------------------------------ 异常层次

    /** 自定义基类异常：用于测试"父类链上溯"与"最近祖先优先"。 */
    public static class AppException extends RuntimeException {
        public AppException(String message) {
            super(message);
        }
    }

    /** 精简子类：供父类链用例使用。 */
    public static class SubAppException extends AppException {
        public SubAppException(String message) {
            super(message);
        }
    }

    /** 标记接口：证明"异常实现的接口"不参与匹配（容器键是 Class&lt;? extends Throwable&gt;，接口无法作键）。 */
    public interface Marker {
    }

    /** 同时实现标记接口的子类：父类链命中 AppException，与 Marker 无关。 */
    public static class MarkedSubAppException extends AppException implements Marker {
        public MarkedSubAppException(String message) {
            super(message);
        }
    }

    // -------------------------------------------------------- createInstance 夹具

    /** 公有无参构造：createInstance 反射创建成功。 */
    public static class PlainHandler {
        public PlainHandler() {
        }
    }

    /** 只有带参构造：createInstance 走 NoSuchMethodException 分支。 */
    public static class NoDefaultCtorHandler {
        public NoDefaultCtorHandler(String ignored) {
        }
    }

    /** 无参构造直接抛异常：createInstance 走 InvocationTargetException 分支。 */
    public static class ExplodingCtorHandler {
        public ExplodingCtorHandler() {
            throw new IllegalStateException("ctor boom");
        }
    }

    // -------------------------------------------------------------- 处理器夹具

    /** 没有任何注解方法的对象：不应贡献任何注册。 */
    public static class NoAnnotatedMethodHandler {
        public String plain() {
            return "plain";
        }
    }

    /** 一条带显式状态码、一条不带（回落 200）。 */
    public static class UserExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        @ExceptionResponseStatus(400)
        public String onIllegalArgument(IllegalArgumentException e) {
            return "iae:" + e.getMessage();
        }

        @ExceptionHandler(RuntimeException.class)
        public String onRuntime(RuntimeException e) {
            return "rte:" + e.getMessage();
        }
    }

    /** {@code @ExceptionResponseStatus} 不带 value：取注解默认值 500。 */
    public static class DefaultStatusHandler {
        @ExceptionHandler(IllegalStateException.class)
        @ExceptionResponseStatus
        public String onState(IllegalStateException e) {
            return "state";
        }
    }

    /** 与 {@link UserExceptionHandler} 争同一个异常类型：容器应当拒绝第二份注册。 */
    public static class DuplicateRuntimeHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        public String onIllegalArgument(IllegalArgumentException e) {
            return "dup:" + e.getMessage();
        }
    }

    /** 注解方法声明在父类上：子类实例经 getMethods() 继承到它，declaringClass 指向父类。 */
    public static class BaseAnnotatedHandler {
        @ExceptionHandler(NumberFormatException.class)
        public String onNumberFormat(NumberFormatException e) {
            return "nf:" + e.getMessage();
        }
    }

    public static class InheritedHandler extends BaseAnnotatedHandler {
    }

    /**
     * package-private 基类 + 公有注解方法：台账 HS-19 设想的"让 unreflect 抛
     * IllegalAccessException"的绕法载体。实测（GraalVM/OpenJDK 21）进不了那个 catch，
     * 但成因与设想相反：编译期在公有子类上生成合成桥，{@code getMethods()} 只暴露桥
     * （declaringClass=公有子类，{@code unreflect} 不抛错；注解与 value 随桥复制）——
     * :109-110 对源码写法不可达，handler 以子类名义照常注册。
     */
    static class PackagePrivateAnnotatedBase {
        @ExceptionHandler(UnsupportedOperationException.class)
        public String onUnsupported(UnsupportedOperationException e) {
            return "unsupported:" + e.getMessage();
        }
    }

    /** 公有子类：HS-19 用例 new 它传入 initContainer；被扫描到的是以本类为声明类、带注解的合成桥。 */
    public static class PackagePrivateBaseSubHandler extends PackagePrivateAnnotatedBase {
    }

    // ------------------------------------------------------------------ 构造助手

    /** 给只需要一个占位句柄的用例（Definition 单元、Matcher 单元）用；不涉及真实方法。 */
    public static MethodHandle constantHandle() {
        return MethodHandles.constant(String.class, "handled");
    }

    /** 造一条挂在指定异常类型上的独立定义，绕过 initContainer 以单独测容器/匹配器。 */
    public static ExceptionHandlerDefinition definition(Class<? extends Throwable> exceptionType) {
        return new ExceptionHandlerDefinition(ExceptionHandlerFixtures.class, constantHandle(), exceptionType);
    }
}
