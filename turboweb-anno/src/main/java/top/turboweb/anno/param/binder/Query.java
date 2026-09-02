package top.turboweb.anno.param.binder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 封装查询参数的注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Query {
    // 参数名
    String value();

    /**
     * 是否不能为null, 默认可以为null
     * @return true代表不能为null
     */
    boolean nonNull() default false;
}
