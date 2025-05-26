package top.turboweb.commons.anno;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注异常类处理器返回的状态码
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExceptionResponseStatus {

    /**
     * 状态码
     *
     * @return 状态码
     */
    int value() default 500;
}
