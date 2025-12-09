package top.turboweb.http.middleware.router.info.autobind;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.anno.param.binder.Upload;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;
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
            if (collectionType == 1) {
                return ctx.loadFiles(name);
            }
            return new HashSet<FileUpload>(ctx.loadFiles(name));
        }
    }


    @Override
    protected ParameterBinder doParse(Parameter parameter) {
        if (!parameter.getType().isAssignableFrom(FileUpload.class)) {
            return null;
        }
        short collectionType = 0;
        if (Collection.class.isAssignableFrom(parameter.getType())) {
            if (parameter.getType() == List.class) {
                collectionType = 1;
            } else if (parameter.getType() == Set.class) {
                collectionType = 2;
            } else {
                throw new TurboRouterDefinitionCreateException("not support type of collection:" + parameter.getType().getName());
            }
        }
        // 获取名称
        Upload upload = parameter.getAnnotation(Upload.class);
        String name = upload.value();
        return new UploadParameterBinder(name, collectionType);
    }
}
