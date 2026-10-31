package top.turboweb.http.router.parameter;

import top.turboweb.http.core.annotation.binder.BeanForm;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 表单参数映射为实体的参数解析器
 */
public class BeanFormParameterParser extends AbstractParameterParser{

    /**
     * 绑定表单参数:取数经请求消息匹配内置表单请求消息（{@link FormRequestMessage}），折叠后经传入的 json 序列化器绑定为 bean,
     * 注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record BeanFormParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            if (!(ctx.message() instanceof FormRequestMessage formMessage)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            Object bean = ParameterHandleHelper.bindBeanMap(jsonSerializer, formMessage.content().formParams(), type);
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
        BeanForm beanForm = parameter.getAnnotation(BeanForm.class);
        return new BeanFormParameterBinder(
                parameter.getType(),
                beanForm.value(),
                beanForm.groups()
        );
    }
}
