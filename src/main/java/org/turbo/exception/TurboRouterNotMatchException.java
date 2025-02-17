package org.turbo.exception;

/**
 * 找不到匹配的路由异常
 */
public class TurboRouterNotMatchException extends RuntimeException {
    public TurboRouterNotMatchException(String message) {
        super(message);
    }
}
