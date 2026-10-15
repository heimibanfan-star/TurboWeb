package top.turboweb.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Objects;
import java.util.Set;

/**
 * BeanValidator 的 Hibernate Validator 实现：以 Jakarta Bean Validation 引擎执行 bean 校验，
 * 存在违规则抛出 {@link BeanValidationException} 承载全部违规（非 fail-fast）。
 * 无参构造经默认 ValidatorFactory 自举引擎，要求 Hibernate Validator 与 EL 解析器位于运行时类路径；
 * 带引擎实例的构造供注入外部已完成配置的引擎。引擎线程安全，本实例可多线程共享。
 * close 释放本实例自举的工厂；外部注入的引擎不归本实例所有，close 对其不作动作。
 */
public class HibernateBeanValidator implements BeanValidator {

    private final Validator validator;

    /**
     * 自举工厂：仅无参构造路径非空，close 时释放；外部注入引擎时为 null。
     */
    private final ValidatorFactory ownedFactory;

    /**
     * 经默认 ValidatorFactory 自举引擎构造校验器。
     */
    public HibernateBeanValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.ownedFactory = factory;
        this.validator = factory.getValidator();
    }

    /**
     * 以外部引擎实例构造校验器。
     *
     * @param validator 引擎实例，不得为 null
     * @throws NullPointerException validator 为 null 时
     */
    public HibernateBeanValidator(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.ownedFactory = null;
    }

    @Override
    public void validate(Object bean) {
        requireBean(bean);
        Set<ConstraintViolation<Object>> violations = validator.validate(bean);
        if (!violations.isEmpty()) {
            throw new BeanValidationException(violations);
        }
    }

    /**
     * groups 为空时由引擎按默认分组执行。
     *
     * @param bean   待校验对象，不得为 null
     * @param groups 校验分组接口，分组数组含 null 元素时由引擎抛出 IllegalArgumentException
     * @throws BeanValidationException  存在校验违规时，异常承载全部违规
     * @throws IllegalArgumentException bean 为 null 时
     */
    @Override
    public void validate(Object bean, Class<?>... groups) {
        requireBean(bean);
        Set<ConstraintViolation<Object>> violations = validator.validate(bean, groups);
        if (!violations.isEmpty()) {
            throw new BeanValidationException(violations);
        }
    }

    /**
     * 关闭本实例自举的 ValidatorFactory；外部注入的引擎不归本实例所有，不作关闭动作。
     */
    @Override
    public void close() {
        if (ownedFactory != null) {
            ownedFactory.close();
        }
    }

    private static void requireBean(Object bean) {
        if (bean == null) {
            throw new IllegalArgumentException("bean must not be null");
        }
    }

}
