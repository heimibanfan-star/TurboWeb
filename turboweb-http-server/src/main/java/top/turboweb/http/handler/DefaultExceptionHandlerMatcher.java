package top.turboweb.http.handler;

/**
 * 默认的异常处理器匹配器实现。
 * <p>
 * 该类通过逐级向上查找异常的父类，找到与异常类型最匹配的异常处理器定义。
 * 如果找不到具体匹配的异常处理器，则继续尝试匹配其父类异常处理器，直到找到 {@code Throwable} 为止。
 * </p>
 */
public class DefaultExceptionHandlerMatcher implements ExceptionHandlerMatcher {

    /**
     * 异常处理器容器，保存所有已注册的异常处理器定义。
     */
    public final ExceptionHandlerContainer exceptionHandlerContainer;

    /**
     * 构造默认异常处理器匹配器。
     *
     * @param exceptionHandlerContainer 异常处理器容器
     */
    public DefaultExceptionHandlerMatcher(ExceptionHandlerContainer exceptionHandlerContainer) {
        this.exceptionHandlerContainer = exceptionHandlerContainer;
    }

    /**
     * 匹配指定异常类对应的异常处理器定义。
     * <p>
     * 该方法会首先尝试直接匹配异常类本身的处理器；
     * 如果未找到，则递归向上查找其父类的异常处理器，直到 {@code Throwable} 为止。
     * </p>
     *
     * @param exceptionClass 异常类
     * @return 匹配到的异常处理器定义；若未找到则返回 {@code null}
     */
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
