package top.turboweb.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将路径参数封装为实体对象
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryModel {
    // 是否开启数据校验，默认关闭
    boolean value() default false;
    // 校验组
    Class<?>[] groups() default {};
}
