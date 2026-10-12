package top.turboweb.serializer.exception;

/**
 * 序列化异常
 */
public class SerializerException extends RuntimeException {
    public SerializerException(String message) {
        super(message);
    }

    public SerializerException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerializerException(Throwable cause) {
        super(cause);
    }
}
