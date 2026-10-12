package top.turboweb.commons.exception;

/**
 * http客户端相关异常
 */
public class TurboHttpClientException extends RuntimeException {
    public TurboHttpClientException(String message) {
        super(message);
    }

    public TurboHttpClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public TurboHttpClientException(Throwable cause) {
        super(cause);
    }
}
