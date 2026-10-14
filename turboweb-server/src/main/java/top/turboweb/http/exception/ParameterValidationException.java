package top.turboweb.http.exception;

import java.util.List;

/**
 * 验证参数异常
 */
public class ParameterValidationException extends RuntimeException {

    private final List<String> errorMsg;

    public ParameterValidationException(List<String> errorMsg) {
        super(errorMsg.toString());
        this.errorMsg = errorMsg;
    }

    public List<String> getErrorMsg() {
        return errorMsg;
    }
}
