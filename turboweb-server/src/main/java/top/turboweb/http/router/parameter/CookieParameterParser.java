package top.turboweb.http.router.parameter;

import top.turboweb.rest.parameter.CookieParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

/**
 * 负责解析@CookieParam注解
 */
public class CookieParameterParser extends AbstractParameterParser {

    /**
     * Cookie 参数绑定器:按注解 value 从上下文 Cookie 集合取值,再按声明类型转化
     */
    private record CookieParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            return simpleTypeHandler.handle(ctx.cookies().getCookie(name));
        }
    }

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(CookieParam.class)) {
            return null;
        }
        // 参数类型
        Class<?> type = parameter.getType();
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 解析注解
        CookieParam param = parameter.getAnnotation(CookieParam.class);
        String name = param.value();
        // 创建绑定器
        return new CookieParameterBinder(name, simpleTypeHandler);
    }
}
