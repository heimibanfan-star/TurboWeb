package top.turboweb.http.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.AppException;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.MarkedSubAppException;
import top.turboweb.http.testkit.fixtures.ExceptionHandlerFixtures.SubAppException;

import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DefaultExceptionHandlerMatcher} 的匹配语义。
 *
 * <p>匹配只沿 {@code getSuperclass()} 向 {@link Throwable} 逐级上行，从不遍历接口；
 * 容器以精确类为键，因此"多候选时的优先级"完全等于"离异常类最近的已注册祖先"，
 * 与注册顺序无关。
 */
class ExceptionHandlerMatcherTest {

    private static DefaultExceptionHandlerMatcher matcher(ExceptionHandlerDefinition... definitions) {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        for (ExceptionHandlerDefinition definition : definitions) {
            container.addExceptionHandler(definition);
        }
        return new DefaultExceptionHandlerMatcher(container);
    }

    @Test
    @DisplayName("构造器持有的容器通过公共字段可读，与传入实例同一")
    void constructorExposesTheContainerItWasGiven() {
        ExceptionHandlerContainer container = new ExceptionHandlerContainer();
        assertSame(container, new DefaultExceptionHandlerMatcher(container).exceptionHandlerContainer);
    }

    @Test
    @DisplayName("精确类型命中，返回注册的同一 Definition")
    void exactTypeHitReturnsSameDefinition() {
        ExceptionHandlerDefinition definition = ExceptionHandlerFixtures.definition(IllegalStateException.class);
        assertSame(definition, matcher(definition).match(IllegalStateException.class));
    }

    @Test
    @DisplayName("未注册精确类型时沿父类链上溯命中")
    void walksUpToRegisteredSuperclass() {
        ExceptionHandlerDefinition onRuntime = ExceptionHandlerFixtures.definition(RuntimeException.class);
        DefaultExceptionHandlerMatcher matcher = matcher(onRuntime);

        assertSame(onRuntime, matcher.match(IllegalArgumentException.class));
        assertSame(onRuntime, matcher.match(ArithmeticException.class));
    }

    @Test
    @DisplayName("多个祖先都注册时取最近的一个，与注册顺序无关")
    void nearestRegisteredAncestorWinsRegardlessOfRegistrationOrder() {
        ExceptionHandlerDefinition onRuntime = ExceptionHandlerFixtures.definition(RuntimeException.class);
        ExceptionHandlerDefinition onException = ExceptionHandlerFixtures.definition(Exception.class);

        assertSame(onRuntime, matcher(onException, onRuntime).match(IllegalStateException.class));
        assertSame(onRuntime, matcher(onRuntime, onException).match(IllegalStateException.class));
    }

    @Test
    @DisplayName("注册 Throwable 可兜住 Error 与自定义异常")
    void throwableRootHandlerCatchesEverything() {
        ExceptionHandlerDefinition onThrowable = ExceptionHandlerFixtures.definition(Throwable.class);
        DefaultExceptionHandlerMatcher matcher = matcher(onThrowable);

        assertSame(onThrowable, matcher.match(StackOverflowError.class));
        assertSame(onThrowable, matcher.match(AppException.class));
    }

    @Test
    @DisplayName("空容器：任何异常都返回 null")
    void emptyContainerReturnsNull() {
        assertNull(matcher().match(IllegalStateException.class));
    }

    @Test
    @DisplayName("只注册了无关类型时返回 null")
    void unrelatedRegisteredTypeReturnsNull() {
        ExceptionHandlerDefinition other = ExceptionHandlerFixtures.definition(DateTimeParseException.class);
        assertNull(matcher(other).match(IllegalStateException.class));
    }

    @Test
    @DisplayName("查询 Throwable 本身且未注册 Throwable 时返回 null")
    void throwableItselfReturnsNullWhenNotRegistered() {
        ExceptionHandlerDefinition onSubclass = ExceptionHandlerFixtures.definition(IllegalArgumentException.class);
        assertNull(matcher(onSubclass).match(Throwable.class));
    }

    @Test
    @DisplayName("异常实现接口不参与匹配：只按父类链命中已注册的基类")
    void interfaceImplementedByExceptionDoesNotChangeTheSuperclassWalk() {
        // 容器键类型是 Class<? extends Throwable>，接口（如 Marker）根本无法注册；
        // 匹配器也从不调用 getInterfaces()。这里两个子类都没有自己的注册，
        // 结果都经 getSuperclass() 落到 AppException —— 与谁实现什么接口无关。
        ExceptionHandlerDefinition onApp = ExceptionHandlerFixtures.definition(AppException.class);
        DefaultExceptionHandlerMatcher matcher = matcher(onApp);

        assertSame(onApp, matcher.match(MarkedSubAppException.class));
        assertSame(onApp, matcher.match(SubAppException.class));
    }
}
