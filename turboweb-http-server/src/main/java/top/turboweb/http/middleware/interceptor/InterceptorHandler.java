package top.turboweb.http.middleware.interceptor;

import top.turboweb.commons.utils.order.Order;
import top.turboweb.http.context.HttpContext;

/**
 * 拦截器接口
 */
public interface InterceptorHandler extends Order {

    /**
     * 在请求到达后续中间件之前执行
     *
     * @param ctx 请求的上下文对象
     * @return boolean 返回true，拦截器继续向后允许，返回false，拦截器结束
     */
    default boolean preHandler(HttpContext ctx) {
        return true;
    }

    /**
     * 后续中间件执行完成之后调用，
     * 如果在preHandler中返回了false，或者出现异常，则所有的postHandler都不会被调用
     *
     * @param ctx 请求的上下文
     * @param result 后续中间件的返回结果
     * @return 可以修改后续中间件的返回结果返回，也可以返回原始的结果
     */
    default Object postHandler(HttpContext ctx, Object result) {
        return result;
    }

    /**
     * 在所有的拦截器执行完毕之后执行
     * 如果在preHandler中返回了false，则该拦截器的afterCompletion方法以及后续的都不会调用，之前的正常调用
     * 如果在preHandler中出现了异常也是同理
     *
     * @param exception 如果出现异常，则传入异常对象，否则该对象处于null
     */
    default void afterCompletion(Throwable exception) {}
}
