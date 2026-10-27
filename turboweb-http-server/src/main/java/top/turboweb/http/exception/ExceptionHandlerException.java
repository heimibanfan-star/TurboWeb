package top.turboweb.http.exception;

public class ExceptionHandlerException extends RuntimeException {

    public ExceptionHandlerException(String message) {
        super(message);
    }

    public ExceptionHandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExceptionHandlerException(Throwable cause) {
        super(cause);
    }
}
