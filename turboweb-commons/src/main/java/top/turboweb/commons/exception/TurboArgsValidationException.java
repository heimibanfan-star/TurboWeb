package top.turboweb.commons.exception;

import java.util.List;

/**
 * 验证参数异常
 */
public class TurboArgsValidationException extends RuntimeException {

    private final List<String> errorMsg;

    public TurboArgsValidationException(List<String> errorMsg) {
        super(errorMsg.toString());
        this.errorMsg = errorMsg;
    }

    public List<String> getErrorMsg() {
        return errorMsg;
    }
}
