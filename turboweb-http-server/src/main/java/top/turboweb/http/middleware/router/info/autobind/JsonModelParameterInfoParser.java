package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.anno.param.binder.JsonModel;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * json映射为实体的参数解析器
 */
public class JsonModelParameterInfoParser extends AbstractParamInfoParser{

    /**
     * json模型参数绑定器
     */
    private record JsonModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterBinder {

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
    protected ParameterBinder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(JsonModel.class)) {
            return null;
        }
        JsonModel jsonModel = parameter.getAnnotation(JsonModel.class);
        return new JsonModelParameterBinder(
                parameter.getType(),
                jsonModel.value(),
                jsonModel.groups()
        );
    }
}
