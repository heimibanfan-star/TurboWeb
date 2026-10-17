package top.turboweb.http.router.parameter;

import top.turboweb.rest.parameter.BeanQuery;
import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 查询模型映射为实体的参数解析器
 */
public class BeanQueryParameterParser extends AbstractParameterParser{


    /**
     * 查询参数映射为实体的绑定器:取数为承载对象的查询映射,折叠后经传入的 json 序列化器绑定为 bean,
     * 注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record BeanQueryParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            Object bean = ParameterHandleHelper.bindBeanMap(jsonSerializer, requestParameter.queryParams(), type);
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
        BeanQuery beanQuery = parameter.getAnnotation(BeanQuery.class);
        return new BeanQueryParameterBinder(
                parameter.getType(),
                beanQuery.value(),
                beanQuery.groups()
        );
    }
}
