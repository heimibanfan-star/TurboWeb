package top.turboweb.commons.exception;

/**
 * 数据重复异常
 */
public class TurboDuplicateException extends RuntimeException {
    public TurboDuplicateException(String message) {
        super(message);
    }
}
