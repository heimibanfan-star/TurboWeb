package top.turboweb.http.middleware;

import io.netty.util.internal.TypeParameterMatcher;
import top.turboweb.http.context.HttpContext;

/**
 * TypedSkipMiddleware 是一个泛型类型中间件基类。
 * 它的特点是“跳过指定类型 T”，只处理非 T 类型的返回值。
 * 主要作用：
 * 1. 调用下一个中间件获取返回值。
 * 2. 如果返回值是 T 类型，则直接跳过，不做处理。
 * 3. 如果返回值不是 T 类型，则调用 afterNext() 进行处理。
 *
 * @param <T> 当前中间件希望跳过的类型
 */
public abstract class TypedSkipMiddleware<T> extends Middleware {

    /**
     * TypeParameterMatcher 在运行时判断对象是否为泛型 T 类型
     */
    private final TypeParameterMatcher matcher = TypeParameterMatcher.find(this, TypedSkipMiddleware.class, "T");

    /**
     * 中间件核心方法
     * 执行流程：
     * 1. 调用 preNext()，可在前置逻辑中中断链条。
     * 2. 调用下一个中间件 next() 获取返回值。
     * 3. 判断返回值类型：
     *      - 如果是 T 类型，直接返回，不处理。
     *      - 如果不是 T 类型，调用 afterNext() 处理。
     *
     * @param ctx 当前请求上下文
     * @return 处理后的结果或原返回值
     */
    @Override
    @SuppressWarnings("unchecked")
    public final Object invoke(HttpContext ctx) {
        if (!preNext(ctx)) {
            return onBreak(ctx);
        }

        Object result = next(ctx);

        // 如果返回值是 T 类型，直接跳过处理
        if (matcher.match(result)) {
            return result;
        }

        // 如果不是 T 类型，调用 afterNext 处理
        return afterNext(ctx, (T) result);
    }

    /**
     * 前置钩子方法
     * 可在调用下一个中间件前执行逻辑，例如条件判断或权限校验。
     *
     * @param ctx 当前请求上下文
     * @return true 继续执行下一个中间件，false 中断链条
     */
    protected boolean preNext(HttpContext ctx) {
        return true;
    }

    /**
     * 链条中断时的回调方法
     * 可在 preNext 返回 false 时执行自定义逻辑。
     *
     * @param ctx 当前请求上下文
     * @return 中断后的返回值，默认 null
     */
    protected Object onBreak(HttpContext ctx) {
        return null;
    }

    /**
     * 抽象方法：处理非 T 类型的返回值
     * 子类必须实现，用于处理下一个中间件返回的非 T 类型对象。
     *
     * @param ctx 当前请求上下文
     * @param result 下一个中间件返回的非 T 类型对象
     * @return 处理后的结果
     */
    protected abstract T afterNext(HttpContext ctx, T result);
}
