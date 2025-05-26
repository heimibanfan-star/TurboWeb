package top.turboweb.commons.exception;

/**
 * 响应重复写入异常
 */
public class TurboResponseRepeatWriteException extends RuntimeException {
    public TurboResponseRepeatWriteException(String message) {
        super(message);
    }
}
