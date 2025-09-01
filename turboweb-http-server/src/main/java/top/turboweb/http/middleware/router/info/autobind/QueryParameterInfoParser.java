package top.turboweb.http.middleware.router.info.autobind;

import top.turboweb.anno.Query;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 查询参数解析器
 */
public class QueryParameterInfoParser implements ParameterInfoParser {

    /**
     * 查询参数绑定器
     */
    private record QueryParameterBinder(
            String name,
            TypeConverters.Converter converter,
            // 集合类型,0 非集合, 1 list, 2 set
            short collectionType
    ) implements ParameterBinder {

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (collectionType == 0) {
                return converter.convert(ctx.query(name));
            }
            // 获取所有数据
            List<String> values = ctx.queries(name);
            if (values.isEmpty()) {
                return collectionType == 1 ? List.of() : Set.of();
            }
            // 转化数据类型
            if (collectionType == 1) {
                return values.stream().map(converter::convert).toList();
            } else {
                return values.stream().map(converter::convert).collect(Collectors.toSet());
            }
        }
    }


    @Override
    public ParameterBinder parse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(Query.class)) {
            return null;
        }
        // 参数类型
        Class<?> type = String.class;
        // 判断是否是集合类型
        short collectionType = 0;
        if (Collection.class.isAssignableFrom(parameter.getType())) {
            if (parameter.getType() == List.class) {
                collectionType = 1;
            } else if (parameter.getType() == Set.class) {
                collectionType = 2;
            } else {
                throw new TurboRouterDefinitionCreateException("not support type of collection:" + parameter.getType().getName());
            }
            // 获取参数类型
            if (parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    if (actualTypeArguments[0] instanceof Class<?> clazz) {
                        type = clazz;
                    }
                }
            }
        } else {
            type = parameter.getType();
        }
        // 查询具体的转换器
        TypeConverters.Converter converter = TypeConverters.getConverter(type);
        if (converter == null) {
            return null;
        }
        // 获取参数名
        Query query = parameter.getAnnotation(Query.class);
        String name = query.value();
        return new QueryParameterBinder(name, converter, collectionType);
    }
}
