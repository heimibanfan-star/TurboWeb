package top.turboweb.http.router.parameter;

import top.turboweb.rest.parameter.HeaderParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 负责解析@HeaderParam注解
 */
public class HeaderParameterParser extends AbstractParameterParser {

    /**
     * 请求头参数绑定器:按注解 value 从请求消息的请求头只读视图取首值,再按声明类型转化
     */
    private record HeaderParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            return simpleTypeHandler.handle(ctx.message().headers().get(name));
        }
    }

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(HeaderParam.class)) {
            return null;
        }
        // 参数类型
        Class<?> type = parameter.getType();
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 解析注解
        HeaderParam param = parameter.getAnnotation(HeaderParam.class);
        String name = param.value();
        // 创建绑定器
        return new HeaderParameterBinder(name, simpleTypeHandler);
    }
}
