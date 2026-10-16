package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanJson;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * json映射为实体的参数解析器
 */
public class BeanJsonParameterParser extends AbstractParameterParser{

    /**
     * json模型参数绑定器:取数经请求体内容的 JsonContent 能力契约，绑定走无校验的 bindJson 通道,
     * 注解要求校验时在绑定器内经传入的 Bean 校验器执行
     */
    private record JsonModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator) {
            HttpContext.Content content = ctx.content();
            if (!(content instanceof HttpContext.JsonContent jsonContent)) {
                throw new RequestParseException("contentType is not application/json");
            }
            Object bean = ctx.bindJson(jsonContent.jsonString(), type);
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
