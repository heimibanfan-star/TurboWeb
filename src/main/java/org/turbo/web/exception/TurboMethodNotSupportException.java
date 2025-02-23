package org.turbo.web.exception;

/**
 * 请求方式不支持的异常
 */
public class TurboMethodNotSupportException extends RuntimeException {

    public TurboMethodNotSupportException(String message) {
        super(message);
    }
}
