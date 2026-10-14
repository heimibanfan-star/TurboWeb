package top.turboweb.http.exception;

/**
 * 路由相关的异常
 */
public class RouteHandlerException extends RuntimeException {

    public static final Integer ROUTER_NOT_MATCH = 10001;
    public static final Integer ROUTER_REPEAT = 10002;
    public static final Integer ROUTER_INVOKE_ERROR = 10003;
    public static final Integer ROUTER_DEFINITION_CREATE_ERROR = 10004;

    private final Integer code;

    public RouteHandlerException(String message, Integer code) {
        super(message);
        this.code = code;
    }

    public RouteHandlerException(Throwable e, Integer code) {
        super(e);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
