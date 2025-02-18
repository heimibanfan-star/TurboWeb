package org.turbo.exception;

/**
 * 路由方法执行异常
 */
public class TurboRouterInvokeException extends RuntimeException {
    public TurboRouterInvokeException(String message) {
        super(message);
    }
}
