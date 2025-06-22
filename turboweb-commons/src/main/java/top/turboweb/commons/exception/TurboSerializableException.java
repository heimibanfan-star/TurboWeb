package top.turboweb.commons.exception;

/**
 * 序列化异常
 */
public class TurboSerializableException extends RuntimeException {
    public TurboSerializableException(String message) {
        super(message);
    }

    public TurboSerializableException(String message, Throwable cause) {
        super(message, cause);
    }

    public TurboSerializableException(Throwable cause) {
        super(cause);
    }
}
