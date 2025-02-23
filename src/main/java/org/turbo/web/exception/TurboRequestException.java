package org.turbo.web.exception;

/**
 * 请求时产生的一些异常
 */
public class TurboRequestException extends RuntimeException {
    public TurboRequestException(String message) {
        super(message);
    }
}
