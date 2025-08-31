package top.turboweb.http.middleware.router.info.autobind;

import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.anno.*;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 注解参数解析器
 */
public class AnnoParameterInfoParser implements ParameterInfoParser {

    interface TypeConverter {
        Object convert(String value);
    }

    private static final Map<Class<?>, TypeConverter> CONVERTER_MAP = new HashMap<>();

    static {
        CONVERTER_MAP.put(String.class, value -> value);
        CONVERTER_MAP.put(Integer.class, value -> value == null? null: Integer.parseInt(value));
        CONVERTER_MAP.put(int.class, value -> value == null? 0: Integer.parseInt(value));
        CONVERTER_MAP.put(Long.class, value -> value == null? null: Long.parseLong(value));
        CONVERTER_MAP.put(long.class, value -> value == null? 0L: Long.parseLong(value));
        CONVERTER_MAP.put(Double.class, value -> value == null? null: Double.parseDouble(value));
        CONVERTER_MAP.put(double.class, value -> value == null? 0.0: Double.parseDouble(value));
        CONVERTER_MAP.put(Boolean.class, value -> value == null? null: Boolean.parseBoolean(value));
        CONVERTER_MAP.put(boolean.class, Boolean::parseBoolean);
        CONVERTER_MAP.put(LocalDate.class, value -> value == null? null: LocalDate.parse(value));
        CONVERTER_MAP.put(LocalDateTime.class, value -> value == null? null: LocalDateTime.parse(value));
        CONVERTER_MAP.put(LocalTime.class, value -> value == null? null: LocalTime.parse(value));
    }

    /**
     * 查询参数绑定器
     */
    private static class QueryParameterBinder implements ParameterBinder {

        private final String name;
        private final TypeConverter converter;
        // 集合类型,0 非集合, 1 list, 2 set
        private final short collectionType;

        private QueryParameterBinder(String name, TypeConverter converter, short collectionType) {
            this.name = name;
            this.converter = converter;
            this.collectionType = collectionType;
        }

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (collectionType == 0) {
                return converter.convert(ctx.query(name));
            }
            // 获取所有数据
            List<String> values = ctx.queries(name);
            if (values.isEmpty()) {
                return collectionType == 1? List.of(): Set.of();
            }
            // 转化数据类型
            if (collectionType == 1) {
                return values.stream().map(converter::convert).toList();
            } else {
                return values.stream().map(converter::convert).collect(Collectors.toSet());
            }
        }
    }

    /**
     * rest参数绑定器
     */
    private static class ParamPrameterBinder implements ParameterBinder {

        private final String name;
        private final TypeConverter converter;

        private ParamPrameterBinder(String name, TypeConverter converter) {
            this.name = name;
            this.converter = converter;
        }

        @Override
        public Object bindParameter(HttpContext ctx) {
            return converter.convert(ctx.param(name));
        }
    }

    /**
     * 查询参数映射为实体的绑定器
     */
    private static class QueryModelParameterBinder implements ParameterBinder {

        private final Class<?> type;
        private final boolean validate;
        private final Class<?>[] groups;

        private QueryModelParameterBinder(Class<?> type, boolean validate, Class<?>[] groups) {
            this.type = type;
            this.validate = validate;
            this.groups = groups;
        }

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadQuery(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidQuery(type);
                } else {
                    return ctx.loadValidQuery(type, groups);
                }
            }
        }
    }

    /**
     * 绑定表单模型参数
     */
    private static class FormModelParameterBinder implements ParameterBinder {

        private final Class<?> type;
        private final boolean validate;
        private final Class<?>[] groups;

        private FormModelParameterBinder(Class<?> type, boolean validate, Class<?>[] groups) {
            this.type = type;
            this.validate = validate;
            this.groups = groups;
        }

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadForm(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidForm(type);
                } else {
                    return ctx.loadValidForm(type, groups);
                }
            }
        }
    }

    /**
     * json模型参数绑定器
     */
    private static class JsonModelParameterBinder implements ParameterBinder {

        private final Class<?> type;
        private final boolean validate;
        private final Class<?>[] groups;

        private JsonModelParameterBinder(Class<?> type, boolean validate, Class<?>[] groups) {
            this.type = type;
            this.validate = validate;
            this.groups = groups;
        }

        @Override
        public Object bindParameter(HttpContext ctx) {
            if (!validate) {
                return ctx.loadJson(type);
            } else {
                if (groups == null || groups.length == 0) {
                    return ctx.loadValidJson(type);
                } else {
                    return ctx.loadValidJson(type, groups);
                }
            }
        }
    }

    /**
     * 绑定上传文件参数
     */
    private static class UploadParameterBinder implements ParameterBinder {

        private final String name;
        // 集合类型 0 单个对象 1 list集合 2 set集合
        private final short collectionType;

        private UploadParameterBinder(String name, short collectionType) {
            this.name = name;
            this.collectionType = collectionType;
        }

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
    public ParameterBinder parse(Parameter parameter) {
        // 判断参数上的注解
        if (parameter.isAnnotationPresent(Param.class)) {
           return parseAnnoParam(parameter);
        } else if (parameter.isAnnotationPresent(Query.class)) {
            return parseAnnoQuery(parameter);
        } else if (parameter.isAnnotationPresent(Upload.class)) {
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
        } else if (parameter.isAnnotationPresent(QueryModel.class)) {
            QueryModel queryModel = parameter.getAnnotation(QueryModel.class);
            return new QueryModelParameterBinder(parameter.getType(), queryModel.value(), queryModel.groups());
        } else if (parameter.isAnnotationPresent(FormModel.class)) {
            FormModel formModel = parameter.getAnnotation(FormModel.class);
            return new FormModelParameterBinder(parameter.getType(), formModel.value(), formModel.groups());
        } else if (parameter.isAnnotationPresent(JsonModel.class)) {
            JsonModel jsonModel = parameter.getAnnotation(JsonModel.class);
            return new JsonModelParameterBinder(parameter.getType(), jsonModel.value(), jsonModel.groups());
        } else {
            return null;
        }
    }

    /**
     * 解析REST参数信息
     *
     * @param parameter 参数
     * @return 参数绑定器
     */
    private ParameterBinder parseAnnoParam(Parameter parameter) {
        // 获取参数类型
        Class<?> type = parameter.getType();
        TypeConverter converter = CONVERTER_MAP.get(type);
        if (converter == null) {
            return null;
        }
        Param param = parameter.getAnnotation(Param.class);
        String name = param.value();
        // 创建绑定器
        return new ParamPrameterBinder(name, converter);
    }

    private ParameterBinder parseAnnoQuery(Parameter parameter) {
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
        TypeConverter converter = CONVERTER_MAP.get(type);
        if (converter == null) {
            return null;
        }
        // 获取参数名
        Query query = parameter.getAnnotation(Query.class);
        String name = query.value();
        return new QueryParameterBinder(name, converter, collectionType);
    }
}
