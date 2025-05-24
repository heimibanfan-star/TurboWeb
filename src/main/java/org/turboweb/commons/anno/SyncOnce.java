package org.turboweb.commons.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记的方法只能在非反应式编程中使用，并且每次请求只能使用一次
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface SyncOnce {
}
