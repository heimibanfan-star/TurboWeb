package top.turboweb.client.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展头条目：以头名与多值声明一条扩展头，值缺省为空数组。
 * 仅作为 {@link ExtendHeaders} 的成员值载体，不可独立标注。
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface Header {

    /**
     * 头名。
     *
     * @return 头名
     */
    String name();

    /**
     * 头值集合，同名多值按声明顺序排列。
     *
     * @return 头值数组，缺省为空数组
     */
    String[] values() default {};
}
