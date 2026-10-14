package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanForm;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * 表单参数映射为实体的参数解析器
 */
public class BeanFormParameterParser extends AbstractParameterParser{

    /**
     * 绑定表单模型参数
     */
    private record FormModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadForm(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidForm(type);
                } else {
                    return ctx.loadValidForm(type, groups);
                }
            }
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
