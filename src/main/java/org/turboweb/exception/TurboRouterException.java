package org.turboweb.exception;

/**
 * 路由相关的异常
 */
public class TurboRouterException extends RuntimeException {

    public static final Integer ROUTER_NOT_MATCH = 10001;
    public static final Integer ROUTER_REPEAT = 10002;
    public static final Integer ROUTER_INVOKE_ERROR = 10003;

    private final Integer code;

    public TurboRouterException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    public TurboRouterException(Throwable e, Integer code) {
        super(e);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
