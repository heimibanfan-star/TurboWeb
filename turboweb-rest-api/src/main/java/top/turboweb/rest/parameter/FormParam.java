package top.turboweb.rest.parameter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注表单参数获取的注解：形参声明为 {@link top.turboweb.rest.MultipartFile}
 * 或其集合时按上传文件绑定，其余声明类型按表单字段值绑定
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormParam {
    // 表单字段名称
    String value();
}
