package org.turbo.web.exception;

/**
 * 路由定义创建异常
 */
public class TurboRouterDefinitionCreateException extends RuntimeException {
    public TurboRouterDefinitionCreateException(String message) {
        super(message);
    }

    public TurboRouterDefinitionCreateException(Throwable cause) {
        super(cause);
    }
}
