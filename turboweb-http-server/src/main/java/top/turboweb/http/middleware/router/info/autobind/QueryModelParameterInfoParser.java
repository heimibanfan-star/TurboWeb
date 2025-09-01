package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.anno.QueryModel;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * 查询模型映射为实体的参数解析器
 */
public class QueryModelParameterInfoParser implements ParameterInfoParser{


    /**
     * 查询参数映射为实体的绑定器
     */
    private record QueryModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterBinder {

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadQuery(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidQuery(type);
                } else {
                    return ctx.loadValidQuery(type, groups);
                }
            }
        }
    }


    @Override
    public ParameterBinder parse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(QueryModel.class)) {
            return null;
        }
        QueryModel queryModel = parameter.getAnnotation(QueryModel.class);
        return new QueryModelParameterBinder(
                parameter.getType(),
                queryModel.value(),
                queryModel.groups()
        );
    }
}
