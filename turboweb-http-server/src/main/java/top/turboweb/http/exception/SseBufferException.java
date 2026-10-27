package top.turboweb.http.exception;

/**
 * SSE消息缓存异常
 */
public class SseBufferException extends RuntimeException {
    public SseBufferException(String message) {
        super(message);
    }
}
