package top.turboweb.http.exception;

/**
 * HTTP响应处理失败异常：响应对象构造、文件读取或写出阶段不能按预期完成时抛出。
 */
public class ResponseHandleException extends RuntimeException {

    public ResponseHandleException(String message) {
        super(message);
    }

    public ResponseHandleException(Throwable cause) {
        super(cause);
    }
}
