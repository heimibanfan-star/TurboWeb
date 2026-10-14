package top.turboweb.http.exception;

/**
 * session相关的异常
 */
public class HttpSessionException extends RuntimeException {

    public HttpSessionException(String message) {
        super(message);
    }

    public HttpSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
