package top.turboweb.http.handler;

/**
 * <p><b>异常处理器匹配器接口。</b></p>
 *
 * <p>
 * 该接口用于根据异常类型匹配合适的异常处理器定义，
 * 在请求执行过程中出现异常时，由匹配器负责定位最符合的处理器，
 * 以便框架能够按照用户自定义规则统一处理异常响应。
 * </p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *     <li>根据异常类型查找对应的异常处理器。</li>
 *     <li>支持精确匹配或继承链上的模糊匹配（取决于实现类）。</li>
 *     <li>返回可执行的 {@link ExceptionHandlerDefinition} 实例。</li>
 * </ul>
 */
public interface ExceptionHandlerMatcher {

    /**
     * 根据异常类型匹配并返回对应的异常处理器定义。
     *
     * @param exceptionClass 异常的类型（继承自 {@link Throwable}）
     * @return 匹配到的异常处理器定义；若未找到匹配项，通常返回 {@code null}
     */
    ExceptionHandlerDefinition match(Class<? extends Throwable> exceptionClass);

}
