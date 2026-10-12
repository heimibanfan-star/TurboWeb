package top.turboweb.commons.exception;

/**
 * session相关的异常
 */
public class TurboSessionException extends RuntimeException{

    public TurboSessionException(String message) {
        super(message);
    }

    public TurboSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
