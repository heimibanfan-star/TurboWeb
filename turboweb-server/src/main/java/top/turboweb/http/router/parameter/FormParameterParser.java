package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.FormParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

/**
 * 负责解析@FormParam注解
 */
public class FormParameterParser extends AbstractParameterParser {

    /**
     * 表单参数绑定器:取数经请求体内容的 FormContent 能力契约,按注解 value 取该字段归档的首值,
     * 再按声明类型转化;内容不实现 FormContent 契约时拒绑
     */
    private record FormParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            HttpContext.Content content = ctx.content();
            if (!(content instanceof HttpContext.FormContent formContent)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            Map<String, List<String>> formParams = formContent.formParams();
            List<String> values = formParams.get(name);
            String value = (values == null || values.isEmpty()) ? null : values.getFirst();
            return simpleTypeHandler.handle(value);
        }
    }

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(FormParam.class)) {
            return null;
        }
        // 参数类型
        Class<?> type = parameter.getType();
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 解析注解
        FormParam param = parameter.getAnnotation(FormParam.class);
        String name = param.value();
        // 创建绑定器
        return new FormParameterBinder(name, simpleTypeHandler);
    }
}
