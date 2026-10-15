package top.turboweb.validation;

/**
 * Bean 校验器接口：面向 JSR-380（Jakarta Bean Validation）的 bean 整体校验契约，
 * 方法直接接收 bean 对象，无返回值；存在违规时抛出 {@link BeanValidationException}
 * 承载全部违规（非 fail-fast），使用方可经其 failMessages 获取各字段失败信息。
 * 继承 {@link AutoCloseable} 并收窄 close 不抛受检异常：实现可持有引擎资源，
 * 使用方可经 try-with-resources 释放。
 */
public interface BeanValidator extends AutoCloseable {

    /**
     * 校验整个 bean，存在违规则抛出异常。
     *
     * @param bean 待校验对象，不得为 null
     * @throws BeanValidationException  存在校验违规时，异常承载全部违规
     * @throws IllegalArgumentException bean 为 null 时
     */
    void validate(Object bean);

    /**
     * 按校验分组校验整个 bean，存在违规则抛出异常。
     *
     * @param bean   待校验对象，不得为 null
     * @param groups 校验分组接口，未指定时按默认分组执行
     * @throws BeanValidationException  存在校验违规时，异常承载全部违规
     * @throws IllegalArgumentException bean 为 null 时
     */
    void validate(Object bean, Class<?>... groups);

    /**
     * 释放校验器持有的资源；无资源可释放的实现可作空操作，重复调用无副作用。
     */
    @Override
    void close();

}
