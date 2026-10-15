package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanForm;
import top.turboweb.http.context.HttpContext;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 表单参数映射为实体的参数解析器
 */
public class BeanFormParameterParser extends AbstractParameterParser{

    /**
     * 绑定表单模型参数:取数走无校验通道,注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record FormModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, BeanValidator beanValidator) {
            Object bean = ctx.loadForm(type);
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
