package org.turboweb.exception;

/**
 * 参数解析异常
 */
public class TurboParamParseException extends RuntimeException {
    public TurboParamParseException(String message) {
        super(message);
    }

    public TurboParamParseException(Throwable e) {
        super(e);
    }
}
