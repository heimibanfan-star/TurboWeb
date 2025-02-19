package org.turbo.core.http.middleware;

import org.turbo.core.http.context.HttpContext;

/**
 * 中间件接口
 */
public abstract class Middleware {

    private Middleware next;

    /**
     * 中间件执行方法
     *
     * @param ctx 上下文
     * @return 执行结果
     */
    public abstract Object invoke(HttpContext ctx);

    public Middleware getNext() {
        return next;
    }

    public void setNext(Middleware next) {
        this.next = next;
    }
}
