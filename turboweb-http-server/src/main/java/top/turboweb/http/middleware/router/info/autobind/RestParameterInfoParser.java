package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.anno.Param;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;

/**
 * 负责解析@Param注解
 */
public class RestParameterInfoParser implements ParameterInfoParser {


    /**
     * rest参数绑定器
     */
    private record RestParameterBinder(
            String name,
            TypeConverters.Converter converter
    ) implements ParameterBinder{

        @Override
        public Object bindParameter(HttpContext ctx) {
            return converter.convert(ctx.param(name));
        }
    }


    @Override
    public ParameterBinder parse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(Param.class)) {
            return null;
        }
        // 获取参数类型
        Class<?> type = parameter.getType();
        TypeConverters.Converter converter = TypeConverters.getConverter(type);
        if (converter == null) {
            return null;
        }
        // 解析注解
        Param param = parameter.getAnnotation(Param.class);
        String name = param.value();
        // 创建绑定器
        return new RestParameterBinder(name, converter);
    }
}
