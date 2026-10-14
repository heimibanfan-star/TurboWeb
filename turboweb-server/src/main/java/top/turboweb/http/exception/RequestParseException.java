package top.turboweb.http.exception;

/**
 * HTTP请求解析失败异常：请求行参数、请求体或 Content-Type 不能按预期格式解析或转换时抛出。
 */
public class RequestParseException extends RuntimeException {

    public RequestParseException(String msg) {
        super(msg);
    }

    public RequestParseException(Throwable cause) {
        super(cause);
    }
}
