package top.turboweb.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 命名空间标注：声明远程端口或其方法归属的命名空间名，
 * 命名空间名经 {@code value} 必填提供，无缺省值。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Namespace {

    /**
     * 命名空间名。
     *
     * @return 命名空间名
     */
    String value();
}
