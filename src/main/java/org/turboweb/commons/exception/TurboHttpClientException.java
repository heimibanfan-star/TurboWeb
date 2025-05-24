package org.turboweb.commons.exception;

/**
 * http客户端相关异常
 */
public class TurboHttpClientException extends RuntimeException {
    public TurboHttpClientException(String message) {
        super(message);
    }
}
