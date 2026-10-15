package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.PathParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.util.Map;

/**
 * 负责解析@PathParam注解
 */
public class PathParameterParser extends AbstractParameterParser {


    /**
     * rest参数绑定器
     */
    private record RestParameterBinder(
            String name,
            TypeConverters.Converter converter
    ) implements ParameterParser.Binder{

        @Override
        public Object bindParameter(HttpContext ctx, Map<String, String> pathParams, BeanValidator beanValidator) {
            return converter.convert(pathParams.get(name));
        }
    }


    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(PathParam.class)) {
            return null;
        }
        // 获取参数类型
        Class<?> type = parameter.getType();
        TypeConverters.Converter converter = TypeConverters.getConverter(type);
        if (converter == null) {
            return null;
        }
        // 解析注解
        PathParam param = parameter.getAnnotation(PathParam.class);
        String name = param.value();
        // 创建绑定器
        return new RestParameterBinder(name, converter);
    }
}
