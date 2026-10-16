package top.turboweb.http.router.parameter;

import top.turboweb.rest.param.QueryParam;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.context.HttpContext;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

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
public class QueryParameterParser extends AbstractParameterParser {

    /**
     * 查询参数绑定器
     */
    private record QueryParameterBinder(
            String name,
            SimpleTypeHandler simpleTypeHandler,
            // 集合类型,0 非集合, 1 list, 2 set
            short collectionType,
            boolean nonNull
    ) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter, BeanValidator beanValidator,
                                    JsonSerializer jsonSerializer) {
            if (collectionType == 0) {
                Object arg = simpleTypeHandler.handle(requestParameter.queryParam(name));
                // 规则判断
                if (nonNull && arg == null) {
                    throw new RouteHandlerException("校验失败:" + List.of(name + " must not be null"),
                            ErrorCode.MissingParamError);
                } else {
                    return arg;
                }
            }
            // 获取所有数据
            List<String> values = requestParameter.queryParams(name);
            if (values.isEmpty()) {
                return collectionType == 1 ? List.of() : Set.of();
            }
            // 转化数据类型
            if (collectionType == 1) {
                return values.stream().map(simpleTypeHandler::handle).toList();
            } else {
                return values.stream().map(simpleTypeHandler::handle).collect(Collectors.toSet());
            }
        }
    }


    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(QueryParam.class)) {
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
                throw new RouteHandlerException("not support type of collection:" + parameter.getType().getName(),
                        ErrorCode.RouterDefinitionCreateError);
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
        // 查询具体的简单类型处理器
        SimpleTypeHandler simpleTypeHandler = ParameterHandleHelper.getSimpleTypeHandler(type);
        if (simpleTypeHandler == null) {
            return null;
        }
        // 获取参数名
        QueryParam query = parameter.getAnnotation(QueryParam.class);
        String name = query.value();
        return new QueryParameterBinder(name, simpleTypeHandler, collectionType, query.nonNull());
    }
}
