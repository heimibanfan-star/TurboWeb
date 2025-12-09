package top.turboweb.http.middleware;

import io.netty.util.internal.TypeParameterMatcher;
import top.turboweb.http.context.HttpContext;

/**
 * TypedMiddleware 是一个泛型类型中间件基类。
 * 它用于在中间件链中对特定类型的返回值进行处理。
 * 主要特点：
 * 1. 调用下一个中间件并获取返回结果。
 * 2. 判断返回值是否为关注的类型 T。
 * 3. 对匹配类型执行 afterNext() 进行处理，不匹配类型直接返回。
 *
 * @param <T> 当前中间件关注的类型
 */
public abstract class TypedMiddleware<T> extends CoreTypeMiddleware {

    /**
     * TypeParameterMatcher 在运行时判断对象是否为泛型 T 类型
     */
    private final TypeParameterMatcher matcher = TypeParameterMatcher.find(this, TypedMiddleware.class, "T");

    /**
     * 中间件核心方法
     *
     * 执行流程：
     * 1. 调用 preNext()，可在前置拦截逻辑中中断链条。
     * 2. 调用下一个中间件 next() 获取结果。
     * 3. 如果返回值为 null，直接返回。
     * 4. 判断返回值类型：
     *      - 如果匹配 T 类型，调用 afterNext() 处理。
     *      - 如果不匹配，直接返回原结果。
     *
     * @param ctx 当前请求上下文
     * @return 处理后的结果，或者下一个中间件的返回值
     */
    @Override
    @SuppressWarnings("unchecked")
    public final Object invoke(HttpContext ctx) {
        if (!preNext(ctx)) {
            return onBreak(ctx);
        }

        Object result = next(ctx);

        if (result == null) {
            return null;
        }

        // 判断是否为 T 类型
        if (matcher.match(result)) {
            return afterNext(ctx, (T) result);
        }

        return result;
    }

    /**
     * 抽象方法：处理匹配类型的返回值
     * 子类必须实现，用于对 T 类型的结果进行处理。
     *
     * @param ctx 当前请求上下文
     * @param result 下一个中间件返回的 T 类型对象
     * @return 处理后的结果
     */
    protected abstract T afterNext(HttpContext ctx, T result);
}
