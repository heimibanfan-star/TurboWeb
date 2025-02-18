package org.turbo.exception;

/**
 * 序列化异常
 */
public class TurboSerializableException extends RuntimeException {
    public TurboSerializableException(String message) {
        super(message);
    }
}
