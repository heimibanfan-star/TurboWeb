package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanJson;
import top.turboweb.http.context.HttpContext;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * json映射为实体的参数解析器
 */
public class BeanJsonParameterParser extends AbstractParameterParser{

    /**
     * json模型参数绑定器:取数走无校验通道,注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record JsonModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, Map<String, String> pathParams, BeanValidator beanValidator) {
            Object bean = ctx.loadJson(type);
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
        if (!parameter.isAnnotationPresent(BeanJson.class)) {
            return null;
        }
        BeanJson jsonModel = parameter.getAnnotation(BeanJson.class);
        return new JsonModelParameterBinder(
                parameter.getType(),
                jsonModel.value(),
                jsonModel.groups()
        );
    }
}
