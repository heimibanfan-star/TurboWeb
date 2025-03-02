package org.turbo.web.exception;

/**
 * sse的异常
 */
public class TurboSseException extends RuntimeException {
    public TurboSseException(String message) {
        super(message);
    }
}
