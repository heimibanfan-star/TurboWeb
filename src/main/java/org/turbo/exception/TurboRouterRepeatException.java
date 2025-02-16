package org.turbo.exception;

/**
 * 路由重复异常
 */
public class TurboRouterRepeatException extends RuntimeException {
    public TurboRouterRepeatException(String message) {
        super(message);
    }
}
