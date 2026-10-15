package top.turboweb.validation;

import jakarta.validation.ConstraintViolation;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bean 校验失败异常：承载 JSR-380 校验产出的违规集合，
 * 经 {@link #failMessages()} 按字段输出失败信息（与逐条取违规消息的校验工具口径一致）。
 * 本异常不内置触发时机，由使用方在校验产生违规后构造并抛出。
 */
public class BeanValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 违规集合以 transient 承载：ConstraintViolation 不可序列化，不参与异常序列化。
     */
    private final transient Set<ConstraintViolation<?>> violations;

    /**
     * 以违规集合构造异常，详情消息由各违规消息聚合而成。
     *
     * @param violations 违规集合，不得为 null，可为空集合
     * @throws IllegalArgumentException violations 为 null 时
     */
    public BeanValidationException(Set<? extends ConstraintViolation<?>> violations) {
        super(buildMessage(violations));
        this.violations = Set.copyOf(violations);
    }

    /**
     * 收集每个字段的校验失败信息。
     *
     * @return 失败消息列表，无违规时为空列表，不会为 null
     */
    public List<String> failMessages() {
        List<String> messages = new ArrayList<>(violations.size());
        for (ConstraintViolation<?> violation : violations) {
            messages.add(violation.getMessage());
        }
        return messages;
    }

    /**
     * 获取承载的违规集合。
     *
     * @return 违规集合，无违规时为空集合，不会为 null
     */
    public Set<ConstraintViolation<?>> getViolations() {
        return violations;
    }

    private static String buildMessage(Set<? extends ConstraintViolation<?>> violations) {
        if (violations == null) {
            throw new IllegalArgumentException("violations must not be null");
        }
        List<String> texts = new ArrayList<>(violations.size());
        for (ConstraintViolation<?> violation : violations) {
            texts.add(violation.getMessage());
        }
        return "Bean validation failed: " + String.join("; ", texts);
    }

}
