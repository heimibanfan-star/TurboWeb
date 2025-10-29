package top.turboweb.http.middleware;

import org.reactivestreams.Publisher;
import top.turboweb.http.context.HttpContext;

/**
 * MixedMiddleware 是一个支持同步和响应式返回值的中间件基类。
 * 它用于 TurboWeb 中统一处理中间件链中不同类型的返回值：
 *  - 同步对象（普通返回值）
 *  - 响应式对象（实现 Publisher 的对象，例如 Mono 或 Flux）
 *
 * 核心特点：
 * 1. 调用下一个中间件获取返回值。
 * 2. 根据返回值类型分发处理：
 *    - 同步对象调用 afterSyncNext()
 *    - 响应式对象调用 afterAsyncNext()
 * 3. 支持链条前置钩子 preNext() 和中断回调 onBreak()
 */
public abstract class MixedMiddleware extends Middleware {

    /**
     * 中间件核心方法
     *
     * 执行流程：
     * 1. 调用 preNext()，如果返回 false，调用 onBreak() 中断链条。
     * 2. 调用下一个中间件 next() 获取返回值。
     * 3. 判断返回值类型：
     *    - 如果是 Publisher<?> 类型，调用 afterAsyncNext() 处理。
     *    - 否则调用 afterSyncNext() 处理。
     *
     * @param ctx 当前请求上下文
     * @return 处理后的结果，类型可能是同步对象或 Publisher
     */
    @Override
    public final Object invoke(HttpContext ctx) {
        if (!preNext(ctx)) {
            return onBreak(ctx);
        }

        // 调用后续中间件
        Object result = next(ctx);

        if (result instanceof Publisher<?> publisher) {
            return afterAsyncNext(ctx, publisher); // 响应式处理
        } else {
            return afterSyncNext(ctx, result);     // 同步处理
        }
    }

    /**
     * 前置钩子方法
     * 可在调用下一个中间件之前执行逻辑，例如权限检查或条件拦截。
     *
     * @param ctx 当前请求上下文
     * @return true 继续执行下一个中间件，false 中断链条
     */
    protected boolean preNext(HttpContext ctx) {
        return true;
    }

    /**
     * 链条中断时的回调方法
     * 当 preNext 返回 false 时执行自定义逻辑。
     *
     * @param ctx 当前请求上下文
     * @return 中断后的返回值，默认 null
     */
    protected Object onBreak(HttpContext ctx) {
        return null;
    }

    /**
     * 处理同步返回值的抽象方法
     * 子类必须实现，用于处理普通对象的返回值。
     *
     * @param ctx 当前请求上下文
     * @param result 下一个中间件返回的同步对象
     * @return 处理后的结果
     */
    protected abstract Object afterSyncNext(HttpContext ctx, Object result);

    /**
     * 处理响应式返回值的抽象方法
     * 子类必须实现，用于处理 Publisher 对象（响应式对象）。
     *
     * @param ctx 当前请求上下文
     * @param publisher 下一个中间件返回的 Publisher 对象
     * @return 处理后的 Publisher
     */
    protected abstract Publisher<?> afterAsyncNext(HttpContext ctx, Publisher<?> publisher);
}
