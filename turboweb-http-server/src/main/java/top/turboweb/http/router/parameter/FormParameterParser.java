package top.turboweb.http.router.parameter;

import top.turboweb.http.core.Content;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.core.annotation.binder.FormParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpFormContent;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 负责解析@FormParam注解：声明类型为 {@link MultipartFile} 或元素类型为 {@link MultipartFile}
 * 的集合时按上传文件绑定，其余声明类型按表单字段值绑定
 */
public class FormParameterParser extends AbstractParameterParser {

    /**
     * 表单参数绑定器:取数经请求体内容转型内置表单内容类（{@link HttpFormContent}）;
     * {@code simpleTypeHandler} 非空时按注解 value 取该字段归档的首值,再按声明类型转化;
     * 为空时按注解 value 取该字段的文件归档。内容不是内置表单内容类时拒绑
     *
     * @param collectionType 文件绑定的集合形态 0 单个对象 1 list集合 2 set集合;文本绑定不使用
     */
    private record FormParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler,
            short collectionType
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            Content content = ctx.message().content();
            if (!(content instanceof HttpFormContent formContent)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            if (simpleTypeHandler == null) {
                Map<String, List<MultipartFile>> files = formContent.formFiles();
                List<MultipartFile> uploads = files.get(name);
                List<MultipartFile> normalized = uploads == null ? List.of() : uploads;
                if (collectionType == 0) {
                    return normalized.isEmpty() ? null : normalized.getFirst();
                }
                if (collectionType == 1) {
                    return normalized;
                }
                return new HashSet<MultipartFile>(normalized);
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
        // 解析注解
        FormParam param = parameter.getAnnotation(FormParam.class);
        String name = param.value();
        // 参数类型
        Class<?> type = parameter.getType();
        // 文件与普通字段共用 @FormParam 指定字段名，声明类型决定归属：
        // 仅 MultipartFile 与元素类型为 MultipartFile 的集合按文件绑定，其余类型交还解析链
        if (Collection.class.isAssignableFrom(type)) {
            if (!elementIsUploadCompatible(parameter)) {
                return null;
            }
            short collectionType;
            if (type == List.class) {
                collectionType = 1;
            } else if (type == Set.class) {
                collectionType = 2;
            } else {
                throw new RouteHandlerException("not support type of collection:" + type.getName(),
                        ErrorCode.RouterDefinitionCreateError);
            }
            return new FormParameterBinder(name, null, collectionType);
        }
        if (type == MultipartFile.class) {
            return new FormParameterBinder(name, null, (short) 0);
        }
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 创建绑定器
        return new FormParameterBinder(name, simpleTypeHandler, (short) 0);
    }

    /**
     * 集合形参的泛型元素类型必须是 {@link MultipartFile} 或其子类型；
     * 裸集合、通配符等无法判定元素类型的形态不认领。
     */
    private static boolean elementIsUploadCompatible(Parameter parameter) {
        if (parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class<?> element) {
                return MultipartFile.class.isAssignableFrom(element);
            }
        }
        return false;
    }
}
