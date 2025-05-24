package org.turboweb.http.handler;

/**
 * 默认异常处理器匹配器
 */
public class DefaultExceptionHandlerMatcher implements ExceptionHandlerMatcher {

    public final ExceptionHandlerContainer exceptionHandlerContainer;

    public DefaultExceptionHandlerMatcher(ExceptionHandlerContainer exceptionHandlerContainer) {
        this.exceptionHandlerContainer = exceptionHandlerContainer;
    }

    @Override
    public ExceptionHandlerDefinition match(Class<? extends Throwable> exceptionClass) {
        // 获取当前异常的处理器
        ExceptionHandlerDefinition exceptionHandlerDefinition = exceptionHandlerContainer.getExceptionHandlerDefinition(exceptionClass);
        if (exceptionHandlerDefinition != null) {
            return exceptionHandlerDefinition;
        }
        // 判断是否是顶级异常父类
        if (exceptionClass == Throwable.class) {
            return null;
        }
        // 获取父类的字节码对象
        Class<?> superclass = exceptionClass.getSuperclass();
        // 判断是否是异常的子类
        if (superclass != null && Throwable.class.isAssignableFrom(superclass)) {
            return match((Class<? extends Throwable>) superclass);
        }
        return null;
    }
}
