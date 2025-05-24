package org.turboweb.exception;

/**
 * 请求方式不支持的异常
 */
public class TurboMethodNotSupportException extends RuntimeException {

    public TurboMethodNotSupportException(String message) {
        super(message);
    }
}
