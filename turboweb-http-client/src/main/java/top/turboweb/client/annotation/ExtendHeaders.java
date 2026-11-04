package top.turboweb.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展头标注：以 {@link Header} 条目集合声明随请求附带的扩展头，
 * 条目顺序即声明顺序，同名条目各自成值，共同构成头名到多值的映射。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtendHeaders {

    /**
     * 扩展头条目集合。
     *
     * @return 扩展头条目数组
     */
    Header[] value();
}
