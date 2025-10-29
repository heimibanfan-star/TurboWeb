package top.turboweb.http.middleware;

import io.netty.util.internal.TypeParameterMatcher;
import top.turboweb.http.context.HttpContext;

/**
 * 泛型中间件抽象类，用于对特定类型的处理结果进行拦截和处理。
 * <p>
 * 该类继承自 {@link Middleware}，通过泛型 T 来指定只处理特定类型的数据。
 * 如果下一个中间件返回的结果是 T 类型，则调用 {@link #invoke0(HttpContext, Object)} 进行处理；
 * 否则直接返回结果，不做任何处理。
 * </p>
 *
 * @param <T> 要处理的目标类型
 */
public abstract class TypeMiddleware <T> extends Middleware{

    /**
     * 类型匹配器，用于判断下一个中间件返回的对象是否是 T 类型。
     */
    private final TypeParameterMatcher matcher = TypeParameterMatcher.find(this, TypeMiddleware.class, "T");;


    /**
     * 调用下一个中间件并处理返回结果。
     * <p>
     * 流程：
     * 1. 调用 {@link #next(HttpContext)} 执行下一个中间件；
     * 2. 判断返回结果是否匹配类型 T；
     * 3. 如果匹配，调用 {@link #invoke0(HttpContext, Object)} 进行自定义处理；
     * 4. 如果不匹配，直接返回原始结果。
     * </p>
     *
     * @param ctx 当前 HTTP 请求上下文
     * @return 处理后的结果对象，如果类型不匹配则返回原始结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public final Object invoke(HttpContext ctx) {
        Object result = next(ctx);
        if (matcher.match(result)) {
            return invoke0(ctx, (T) result);
        }
        return result;
    }

    /**
     * 对匹配类型的结果进行处理的抽象方法，由子类实现。
     * <p>
     * 子类可以重写该方法，对特定类型的返回结果进行修改、包装或者其他处理逻辑。
     * </p>
     *
     * @param ctx 当前 HTTP 请求上下文
     * @param result 下一个中间件返回的 T 类型对象
     * @return 处理后的结果
     */
    protected abstract T invoke0(HttpContext ctx, T result);
}
