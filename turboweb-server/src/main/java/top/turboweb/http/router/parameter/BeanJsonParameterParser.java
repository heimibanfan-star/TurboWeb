package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanJson;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * json映射为实体的参数解析器
 */
public class BeanJsonParameterParser extends AbstractParameterParser{

    /**
     * json参数绑定器:取数经请求体内容的 JsonContent 能力契约，体字符串经传入的 json 序列化器反序列化为 bean,
     * 注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record BeanJsonParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            HttpContext.Content content = ctx.content();
            if (!(content instanceof HttpContext.JsonContent jsonContent)) {
                throw new RequestParseException("contentType is not application/json");
            }
            Object bean = jsonSerializer.jsonToBean(jsonContent.jsonString(), type);
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
        BeanJson beanJson = parameter.getAnnotation(BeanJson.class);
        return new BeanJsonParameterBinder(
                parameter.getType(),
                beanJson.value(),
                beanJson.groups()
        );
    }
}
