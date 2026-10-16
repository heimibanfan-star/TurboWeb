package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanForm;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 表单参数映射为实体的参数解析器
 */
public class BeanFormParameterParser extends AbstractParameterParser{

    /**
     * 绑定表单模型参数:取数经请求体内容的 FormContent 能力契约，绑定走无校验的 bindBean 通道,
     * 注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record FormModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator) {
            HttpContext.Content content = ctx.content();
            if (!(content instanceof HttpContext.FormContent formContent)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            Object bean = ctx.bindBean(formContent.formParams(), type);
            if (validate) {
                if (groups == null || groups.length == 0) {
                    beanValidator.validate(bean);
                } else {
                    beanValidator.validate(bean, groups);
                }
            }
            return bean;
        }
    }


    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(BeanForm.class)) {
            return null;
        }
        BeanForm formModel = parameter.getAnnotation(BeanForm.class);
        return new FormModelParameterBinder(
                parameter.getType(),
                formModel.value(),
                formModel.groups()
        );
    }
}
