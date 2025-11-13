package top.turboweb.anno.param.binder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将json数据封装为实体对象
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonModel {
    // 是否开启数据校验
    boolean value() default false;
    // 校验组
    Class<?>[] groups() default {};
}
