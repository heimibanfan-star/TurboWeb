package top.turboweb.http.middleware.router.info.autobind;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.anno.param.binder.Upload;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件上传的参数解析器
 */
public class UploadParameterInfoParser extends AbstractParamInfoParser {

    /**
     * 绑定上传文件参数
     *
     * @param collectionType 集合类型 0 单个对象 1 list集合 2 set集合
     */
    private record UploadParameterBinder(
            String name,
            short collectionType
    ) implements ParameterBinder {

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (collectionType == 0) {
                return ctx.loadFile(name);
            }
            List<FileUpload> files = ctx.loadFiles(name);
            if (files == null) {
                // 字段不存在时 loadFiles 返回 null，归一为空集合
                files = List.of();
            }
            if (collectionType == 1) {
                return files;
            }
            return new HashSet<FileUpload>(files);
        }
    }


    @Override
    protected ParameterBinder doParse(Parameter parameter) {
        Class<?> type = parameter.getType();
        boolean collection = Collection.class.isAssignableFrom(type);
        if (collection) {
            // 集合形参须显式标注 @Upload 才认领，避免截走其他解析器负责的 List/Set 参数；
            // 声明的元素类型可判定为具体类且与 FileUpload 不符时同样不认领
            if (!parameter.isAnnotationPresent(Upload.class) || !elementIsUploadCompatible(parameter)) {
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
                throw new TurboRouterDefinitionCreateException("not support type of collection:" + type.getName());
            }
        }
        // 获取名称
        Upload upload = parameter.getAnnotation(Upload.class);
        String name = upload.value();
        return new UploadParameterBinder(name, collectionType);
    }

    /**
     * 集合形参的泛型元素类型若可判定为具体类，须为 FileUpload 或其子类型；
     * 裸集合、通配符等不可判定形态以 @Upload 注解的显式表态为准。
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
