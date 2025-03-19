package org.turbo.web.exception;

/**
 * 网关异常
 */
public class TurboGatewayException extends RuntimeException {
    public TurboGatewayException(String message) {
        super(message);
    }
}
