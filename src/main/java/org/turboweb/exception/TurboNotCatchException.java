package org.turboweb.exception;

/**
 * 非捕获异常
 */
public class TurboNotCatchException extends RuntimeException {
    public TurboNotCatchException(String message) {
        super(message);
    }

    public TurboNotCatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public TurboNotCatchException(Throwable cause) {
        super(cause);
    }
}
