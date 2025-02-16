package org.turbo.exception;

/**
 * 控制器实例创建异常
 */
public class TurboControllerCreateException extends RuntimeException {
    public TurboControllerCreateException(String message) {
        super(message);
    }
}
