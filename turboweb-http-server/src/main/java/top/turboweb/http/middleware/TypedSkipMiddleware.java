package top.turboweb.http.middleware;

import io.netty.util.internal.TypeParameterMatcher;
import top.turboweb.http.context.HttpContext;

/**
 * TypedSkipMiddleware 是一个泛型中间件基类，用于“跳过特定类型 T”。
 * <p>
 * 核心功能：
 * 1. 调用下一个中间件获取返回值。
 * 2. 如果返回值属于类型 T，则直接跳过，不做任何处理。
 * 3. 如果返回值不是类型 T，则调用 {@link #afterNext(HttpContext, Object)} 进行处理。
 * </p>
 *
 * <p>适用场景：</p>
 * <ul>
 *     <li>只处理特定类型以外的返回值</li>
 *     <li>可在中间件链中过滤掉某些类型的对象</li>
 * </ul>
 *
 * @param <T> 当前中间件希望跳过的类型
 */
public abstract class TypedSkipMiddleware<T> extends Middleware {

    /**
     * TypeParameterMatcher 用于在运行时判断对象是否为泛型 T 类型
     */
    private final TypeParameterMatcher matcher = TypeParameterMatcher.find(this, TypedSkipMiddleware.class, "T");

    /**
     * 中间件核心方法，控制执行流程。
     * <p>
     * 执行逻辑：
     * 1. 调用 {@link #preNext(HttpContext)}，可在前置逻辑中中断链条。
     * 2. 调用 {@link #next(HttpContext)} 获取下一个中间件的返回值。
     * 3. 判断返回值类型：
     *    - 若属于 T 类型，直接返回，不做处理。
     *    - 若不属于 T 类型，调用 {@link #afterNext(HttpContext, Object)} 处理。
     * </p>
     *
     * @param ctx 当前请求上下文
     * @return 处理后的结果或原返回值
     */
    @Override
    public final Object invoke(HttpContext ctx) {
        if (!preNext(ctx)) {
            return onBreak(ctx);
        }

        Object result = next(ctx);

        // 跳过 T 类型的对象
        if (matcher.match(result)) {
            return result;
        }

        // 处理非 T 类型的对象
        return afterNext(ctx, result);
    }

    /**
     * 前置钩子方法，在调用下一个中间件之前执行。
     * <p>可用于条件判断、权限校验等逻辑。</p>
     *
     * @param ctx 当前请求上下文
     * @return true 继续执行下一个中间件，false 中断链条
     */
    protected boolean preNext(HttpContext ctx) {
        return true;
    }

    /**
     * 链条中断时的回调方法。
     * <p>在 {@link #preNext(HttpContext)} 返回 false 时触发。</p>
     *
     * @param ctx 当前请求上下文
     * @return 中断后的返回值，默认 null
     */
    protected Object onBreak(HttpContext ctx) {
        return null;
    }

    /**
     * 抽象方法，用于处理非 T 类型的返回值。
     * <p>子类必须实现该方法，定义具体处理逻辑。</p>
     *
     * @param ctx 当前请求上下文
     * @param result 下一个中间件返回的非 T 类型对象
     * @return 处理后的结果
     */
    protected abstract Object afterNext(HttpContext ctx, Object result);
}
