package org.turbo.exception;

/**
 * 重复的异常处理器异常
 */
public class TurboExceptionHandlerRepeatException extends RuntimeException {
    public TurboExceptionHandlerRepeatException(String message) {
        super(message);
    }
}
