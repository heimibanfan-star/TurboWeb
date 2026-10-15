package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanQuery;
import top.turboweb.http.context.HttpContext;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 查询模型映射为实体的参数解析器
 */
public class BeanQueryParameterParser extends AbstractParameterParser{


    /**
     * 查询参数映射为实体的绑定器:取数走无校验通道,注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record QueryModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator) {
            Object bean = ctx.bindBean(requestParameter.queryParams(), type);
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
        if (!parameter.isAnnotationPresent(BeanQuery.class)) {
            return null;
        }
        BeanQuery queryModel = parameter.getAnnotation(BeanQuery.class);
        return new QueryModelParameterBinder(
                parameter.getType(),
                queryModel.value(),
                queryModel.groups()
        );
    }
}
