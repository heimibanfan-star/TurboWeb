package top.turboweb.http.exception;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Objects;

/**
 * 路由相关的异常
 */
public class RouteHandlerException extends RuntimeException {

    /**
     * 路由异常错误码:每个码携带数值编号与兜底响应使用的 HTTP 状态码。
     * 客户端状态码(4xx)在异常兜底时按对应状态码生成消息错误页,
     * 服务器状态码(5xx)走 500 异常栈兜底页并记录错误日志。
     */
    public enum ErrorCode {
        /** 路由未匹配:路径无对应处理器,或请求方式与已登记的均不一致 */
        RouterNotMatch(10001, HttpResponseStatus.NOT_FOUND),
        /** 调用出错:目标方法抛出的非运行时异常,包装后由本码承载 */
        RouterInvokeError(10003, HttpResponseStatus.INTERNAL_SERVER_ERROR),
        /** 定义展开错误:路由处理器构造期目标方法不可访问,或参数定义使用不支持的集合类型 */
        RouterDefinitionCreateError(10004, HttpResponseStatus.INTERNAL_SERVER_ERROR),
        /** 缺少必要参数:必填查询参数缺值 */
        MissingParamError(10005, HttpResponseStatus.BAD_REQUEST);

        private final int value;
        private final HttpResponseStatus httpStatus;

        ErrorCode(int value, HttpResponseStatus httpStatus) {
            this.value = value;
            this.httpStatus = httpStatus;
        }

        public int getValue() {
            return value;
        }

        public HttpResponseStatus getHttpStatus() {
            return httpStatus;
        }
    }

    private final ErrorCode code;

    public RouteHandlerException(String message, ErrorCode code) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public RouteHandlerException(Throwable e, ErrorCode code) {
        super(e);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public ErrorCode getCode() {
        return code;
    }
}
