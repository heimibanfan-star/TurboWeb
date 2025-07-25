package top.turboweb.commons.utils.base;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据校验工具类
 */
public class ValidationUtils {

    private ValidationUtils() {
    }

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    /**
     * 校验对象
     *
     * @param obj 校验对象
     * @return 异常信息
     */
    public static List<String> validate(Object obj) {
        List<String> errorMsg = new ArrayList<>();
        Set<ConstraintViolation<Object>> validate = VALIDATOR.validate(obj);
        validate.forEach(cv -> errorMsg.add(cv.getMessage()));
        return errorMsg;
    }

    /**
     * 校验对象
     *
     * @param obj 校验对象
     * @param groups 校验组
     * @return 错误信息
     */
    public static List<String> validate(Object obj, Class<?>... groups) {
        List<String> errorMsg = new ArrayList<>();
        Set<ConstraintViolation<Object>> validate = VALIDATOR.validate(obj, groups);
        validate.forEach(cv -> errorMsg.add(cv.getMessage()));
        return errorMsg;
    }
}
