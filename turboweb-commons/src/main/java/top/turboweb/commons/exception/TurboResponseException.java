package top.turboweb.commons.exception;

public class TurboResponseException extends RuntimeException {
    public TurboResponseException(String message) {
        super(message);
    }

    public TurboResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
