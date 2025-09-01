package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * 表单参数映射为实体的参数解析器
 */
public class FormModelParameterInfoParser implements ParameterInfoParser{

    /**
     * 绑定表单模型参数
     */
    private record FormModelParameterBinder(
            Class<?> type,
            boolean validate,
            Class<?>[] groups
    ) implements ParameterBinder {

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
    public ParameterBinder parse(Parameter parameter) {
        return null;
    }
}
