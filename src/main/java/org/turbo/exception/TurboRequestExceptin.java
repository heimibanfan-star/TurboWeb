package org.turbo.exception;

/**
 * 请求时产生的一些异常
 */
public class TurboRequestExceptin extends RuntimeException {
    public TurboRequestExceptin(String message) {
        super(message);
    }
}
