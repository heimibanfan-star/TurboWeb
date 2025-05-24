package org.turboweb.commons.exception;

/**
 * 静态资源相关异常
 */
public class TurboStaticResourceException extends RuntimeException {
    public TurboStaticResourceException(String message) {
        super(message);
    }

    public TurboStaticResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
