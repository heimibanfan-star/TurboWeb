package org.turbo.web.exception;

/**
 * 异常处理器相关异常
 */
public class TurboExceptionHandlerException extends RuntimeException {

    public TurboExceptionHandlerException(String message) {
        super(message);
    }

    public TurboExceptionHandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public TurboExceptionHandlerException(Throwable cause) {
        super(cause);
    }
}
