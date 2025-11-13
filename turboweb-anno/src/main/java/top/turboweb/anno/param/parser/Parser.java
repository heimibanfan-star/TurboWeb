package top.turboweb.anno.param.parser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于指定被标注的参数被哪个解析器解析
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Parser {

    /** 解析器的类型， 需要是解析器的子类 */
    Class<?> value();
}
