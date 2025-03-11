package org.turbo.web.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记请求的终止方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface End {
}
