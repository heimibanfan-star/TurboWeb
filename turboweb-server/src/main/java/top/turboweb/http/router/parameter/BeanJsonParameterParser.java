package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.BeanJson;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * json映射为实体的参数解析器
 */
public class BeanJsonParameterParser extends AbstractParameterParser{

    /**
     * json模型参数绑定器
     */
    private record JsonModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadJson(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidJson(type);
                } else {
                    return ctx.loadValidJson(type, groups);
                }
            }
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
