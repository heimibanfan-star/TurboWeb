package top.turboweb.http.router.parameter;

import top.turboweb.rest.annotation.binder.PathParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 负责解析@PathParam注解
 */
public class PathParameterParser extends AbstractParameterParser {


    /**
     * 路径参数绑定器
     */
    private record PathParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler
    ) implements ParameterParser.Binder{

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            return simpleTypeHandler.handle(requestParameter.pathParam(name));
        }
    }


    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(PathParam.class)) {
            return null;
        }
        // 获取参数类型
        Class<?> type = parameter.getType();
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 解析注解
        PathParam param = parameter.getAnnotation(PathParam.class);
        String name = param.value();
        // 创建绑定器
        return new PathParameterBinder(name, simpleTypeHandler);
    }
}
