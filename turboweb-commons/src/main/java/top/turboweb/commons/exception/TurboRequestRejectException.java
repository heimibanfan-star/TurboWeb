package top.turboweb.commons.exception;

/**
 * 请求拒绝的异常
 */
public class TurboRequestRejectException extends RuntimeException{
    public TurboRequestRejectException(String message) {
        super(message);
    }
}
