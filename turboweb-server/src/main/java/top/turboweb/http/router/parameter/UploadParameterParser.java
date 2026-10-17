package top.turboweb.http.router.parameter;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.rest.HttpContent;
import top.turboweb.rest.parameter.UploadParam;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpFormContent;
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
 * 文件上传的参数解析器
 */
public class UploadParameterParser extends AbstractParameterParser {

    /**
     * 绑定上传文件参数：取数经请求体内容转型内置表单内容类（{@link HttpFormContent}）按字段名查文件归档，
     * 缺名归一为空集合；单文件通道未命中返回 {@code null}，命中取归档首文件
     *
     * @param collectionType 集合类型 0 单个对象 1 list集合 2 set集合
     */
    private record UploadParameterBinder(
            String name,
            short collectionType
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            HttpContent content = ctx.message().content();
            if (!(content instanceof HttpFormContent formContent)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            Map<String, List<FileUpload>> files = formContent.formFiles();
            List<FileUpload> uploads = files.get(name);
            List<FileUpload> normalized = uploads == null ? List.of() : uploads;
            if (collectionType == 0) {
                return normalized.isEmpty() ? null : normalized.getFirst();
            }
            if (collectionType == 1) {
                return normalized;
            }
            return new HashSet<FileUpload>(normalized);
        }
    }


    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        // 显式标注 @UploadParam 的形参才认领（与其余参数解析器同款注解前置判定），
        // 并保证随后取注解值必定非空
        if (!parameter.isAnnotationPresent(UploadParam.class)) {
            return null;
        }
        Class<?> type = parameter.getType();
        boolean collection = Collection.class.isAssignableFrom(type);
        if (collection) {
            // 声明的元素类型可判定为具体类且与 FileUpload 不符时不认领
            if (!elementIsUploadCompatible(parameter)) {
                return null;
            }
        } else if (!type.isAssignableFrom(FileUpload.class)) {
            return null;
        }
        short collectionType = 0;
        if (collection) {
            if (type == List.class) {
                collectionType = 1;
            } else if (type == Set.class) {
                collectionType = 2;
            } else {
                throw new RouteHandlerException("not support type of collection:" + type.getName(),
                        ErrorCode.RouterDefinitionCreateError);
            }
        }
        // 获取名称
        UploadParam upload = parameter.getAnnotation(UploadParam.class);
        String name = upload.value();
        return new UploadParameterBinder(name, collectionType);
    }

    /**
     * 集合形参的泛型元素类型若可判定为具体类，须为 FileUpload 或其子类型；
     * 裸集合、通配符等不可判定形态以 @UploadParam 注解的显式表态为准。
     */
    private static boolean elementIsUploadCompatible(Parameter parameter) {
        if (parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class<?> element) {
                return FileUpload.class.isAssignableFrom(element);
            }
        }
        return true;
    }
}
