package top.turboweb.http.middleware.router.info;

import top.turboweb.commons.anno.*;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 自动绑定参数的路由定义信息
 */
public class AutoBindRouterDefinition implements RouterDefinition {

    private final MethodHandle methodHandle;
    private final ArgsHandler[] argsHandlers;

    @FunctionalInterface
    private interface ParamBindStrategy {
        Object bind(HttpContext ctx, ParamInfo paramInfo);
    }

    /**
     * 参数信息
     */
    private record ParamInfo(String name,
                             Class<?> type,
                             // 0 忽略，1 List, 2 Set
                             short collectionType,
                             boolean validation,
                             Class<?>[] validationGroups) {
    }

    /**
     * 参数处理器
     */
    private record ArgsHandler(ParamInfo paramInfo, ParamBindStrategy strategy) {

        public Object bind(HttpContext ctx) {
            return strategy.bind(ctx, paramInfo);
        }
    }

    private enum Strategy {

        // 路径参数策略
        PARAM_TYPE_STRING(((ctx, paramInfo) -> ctx.param(paramInfo.name))),
        PARAM_TYPE_INTEGER(((ctx, paramInfo) -> ctx.paramInt(paramInfo.name))),
        PARAM_TYPE_LONG(((ctx, paramInfo) -> ctx.paramLong(paramInfo.name))),
        PARAM_TYPE_BOOLEAN(((ctx, paramInfo) -> ctx.paramBool(paramInfo.name))),
        PARAM_TYPE_DOUBLE(((ctx, paramInfo) -> ctx.paramDouble(paramInfo.name))),
        PARAM_TYPE_DATE(((ctx, paramInfo) -> ctx.paramDate(paramInfo.name))),


        // 查询参数策略
        QUERY_TYPE_STRING(((ctx, paramInfo) -> {
            if (paramInfo.collectionType == 0) {
                return ctx.query(paramInfo.name);
            } else if (paramInfo.collectionType == 1) {
                return ctx.queries(paramInfo.name);
            } else {
                return new HashSet<>(ctx.queries(paramInfo.name));
            }
        })),
        QUERY_TYPE_INTEGER(((ctx, paramInfo) -> {
            if (paramInfo.collectionType == 0) {
                return ctx.queryInt(paramInfo.name);
            } else if (paramInfo.collectionType == 1) {
                return ctx.queriesInt(paramInfo.name);
            } else {
                return new HashSet<>(ctx.queriesInt(paramInfo.name));
            }
        })),
        QUERY_TYPE_LONG(((ctx, paramInfo) -> {
            if (paramInfo.collectionType == 0) {
                return ctx.queryLong(paramInfo.name);
            } else if (paramInfo.collectionType == 1) {
                return ctx.queriesLong(paramInfo.name);
            } else {
                return new HashSet<>(ctx.queriesLong(paramInfo.name));
            }
        })),
        QUERY_TYPE_DOUBLE(((ctx, paramInfo) -> {
            if (paramInfo.collectionType == 0) {
                return ctx.queryDouble(paramInfo.name);
            } else if (paramInfo.collectionType == 1) {
                return ctx.queriesDouble(paramInfo.name);
            } else {
                return new HashSet<>(ctx.queriesDouble(paramInfo.name));
            }
        })),
        QUERY_TYPE_BOOLEAN(((ctx, paramInfo) -> {
            if (paramInfo.collectionType == 0) {
                return ctx.queryBool(paramInfo.name);
            } else if (paramInfo.collectionType == 1) {
                return ctx.queriesBool(paramInfo.name);
            } else {
                return new HashSet<>(ctx.queriesBool(paramInfo.name));
            }
        })),


        // 查询参数映射为实体
        QUERY_MODEL(((ctx, paramInfo) -> {
            // 判断是否开启数据校验
            if (paramInfo.validation) {
                // 判断校验组是否为空
                if (paramInfo.validationGroups.length == 0) {
                    return ctx.loadValidQuery(paramInfo.type);
                } else {
                    return ctx.loadValidQuery(paramInfo.type, paramInfo.validationGroups);
                }
            } else {
                return ctx.loadQuery(paramInfo.type);
            }
        })),
        // 表单参数映射为实体
        FORM_MODEL(((ctx, paramInfo) -> {
            // 判断是否开启数据校验
            if (paramInfo.validation) {
                // 判断校验组是否为空
                if (paramInfo.validationGroups.length == 0) {
                    return ctx.loadValidForm(paramInfo.type);
                } else {
                    return ctx.loadValidForm(paramInfo.type, paramInfo.validationGroups);
                }
            } else {
                return ctx.loadForm(paramInfo.type);
            }
        })),
        // 将json参数映射为实体
        JSON_MODEL(((ctx, paramInfo) -> {
            // 判断是否开启数据校验
            if (paramInfo.validation) {
                // 判断校验组是否为空
                if (paramInfo.validationGroups.length == 0) {
                    return ctx.loadValidJson(paramInfo.type);
                } else {
                    return ctx.loadValidJson(paramInfo.type, paramInfo.validationGroups);
                }
            } else {
                return ctx.loadJson(paramInfo.type);
            }
        })),


        // 请求上下文的注入
        HTTP_CONTEXT((ctx, paramInfo) -> ctx),
        // session的注入
        HTTP_SESSION((ctx, paramInfo) -> ctx.httpSession()),
        // cookie的注入
        HTTP_COOKIE_MANAGER((ctx, paramInfo) -> ctx.cookie()),
        // sse响应的注入
        SSE_RESPONSE((ctx, paramInfo) -> ctx.createSseResponse()),
        // sseEmitter的注入
        SSE_EMITTER((ctx, paramInfo) -> ctx.createSseEmitter()),

        // 策略匹配不到的默认策略
        DEFAULT((ctx, paramInfo) -> null);
        final ParamBindStrategy strategy;

        Strategy(ParamBindStrategy strategy) {
            this.strategy = strategy;
        }
    }


    public AutoBindRouterDefinition(Object instance, Method method) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            methodHandle = lookup.unreflect(method).bindTo(instance).asSpreader(Object[].class, method.getParameterCount());
        } catch (IllegalAccessException e) {
            throw new TurboRouterDefinitionCreateException(e);
        }
        this.argsHandlers = initArgsHandler(method);
    }

    /**
     * 初始化参数绑定器
     *
     * @param method 方法
     * @return 参数绑定器
     */
    private ArgsHandler[] initArgsHandler(Method method) {
        // 获取所有的参数
        Parameter[] parameters = method.getParameters();
        // 创建参数绑定器
        ArgsHandler[] argsHandlers = new ArgsHandler[parameters.length];
        // 存储参数的绑定策略
        ArgsHandler argsHandler;
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            // 判断参数是否是可注入类型
            argsHandler = handleCouldInjectType(parameter);
            if (argsHandler != null) {
                argsHandlers[i] = argsHandler;
                continue;
            }
            // 获取参数的所有注解
            Annotation[] annotations = parameter.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof Param param) {
                    argsHandler = handleAnnoParam(parameter, param);
                } else if (annotation instanceof Query query) {
                    argsHandler = handleAnnoQuery(parameter, query);
                } else if (annotation instanceof QueryModel queryModel) {
                    ParamInfo info = new ParamInfo(null, parameter.getType(), (short) 0, queryModel.value(), queryModel.groups());
                    argsHandler = new ArgsHandler(info, Strategy.QUERY_MODEL.strategy);
                } else if (annotation instanceof FormModel formModel) {
                    ParamInfo info = new ParamInfo(null, parameter.getType(), (short) 0, formModel.value(), formModel.groups());
                    argsHandler = new ArgsHandler(info, Strategy.FORM_MODEL.strategy);
                } else if (annotation instanceof JsonModel jsonModel) {
                    ParamInfo info = new ParamInfo(null, parameter.getType(), (short) 0, jsonModel.value(), jsonModel.groups());
                    argsHandler = new ArgsHandler(info, Strategy.JSON_MODEL.strategy);
                }
                if (argsHandler != null) {
                    break;
                }
            }
            // 判断是否找到合适的处理器
            argsHandlers[i] = Objects.requireNonNullElseGet(argsHandler, () -> new ArgsHandler(null, Strategy.DEFAULT.strategy));
        }
        return argsHandlers;
    }

    /**
     * 判断是否是可自动注入的类型
     *
     * @param parameter 参数
     * @return 参数绑定策略
     */
    private ArgsHandler handleCouldInjectType(Parameter parameter) {
        if (parameter.getType() == HttpContext.class) {
            return new ArgsHandler(null, Strategy.HTTP_CONTEXT.strategy);
        } else if (parameter.getType() == HttpSession.class) {
            return new ArgsHandler(null, Strategy.HTTP_SESSION.strategy);
        } else if (parameter.getType() == HttpCookieManager.class) {
            return new ArgsHandler(null, Strategy.HTTP_COOKIE_MANAGER.strategy);
        } else if (parameter.getType() == SseResponse.class) {
            return new ArgsHandler(null, Strategy.SSE_RESPONSE.strategy);
        } else if (parameter.getType() == SseEmitter.class) {
            return new ArgsHandler(null, Strategy.SSE_EMITTER.strategy);
        }
        return null;
    }

    /**
     * 处理路径参数注解
     *
     * @param parameter 参数
     * @param param     参数注解
     * @return 参数绑定策略
     */
    private ArgsHandler handleAnnoParam(Parameter parameter, Param param) {
        // 创建参数信息对象
        ParamInfo info = new ParamInfo(param.value(), null, (short) 0, false, null);
        return switch (parameter.getType().getTypeName()) {
            case "java.lang.String" -> new ArgsHandler(info, Strategy.PARAM_TYPE_STRING.strategy);
            case "java.lang.Integer", "int" -> new ArgsHandler(info, Strategy.PARAM_TYPE_INTEGER.strategy);
            case "java.lang.Long", "long" -> new ArgsHandler(info, Strategy.PARAM_TYPE_LONG.strategy);
            case "java.lang.Boolean", "boolean" -> new ArgsHandler(info, Strategy.PARAM_TYPE_BOOLEAN.strategy);
            case "java.lang.Double", "double" -> new ArgsHandler(info, Strategy.PARAM_TYPE_DOUBLE.strategy);
            case "java.time.LocalDate" -> new ArgsHandler(info, Strategy.PARAM_TYPE_DATE.strategy);
            default -> null;
        };
    }

    /**
     * 处理查询参数注解
     *
     * @param parameter 参数
     * @param query     查询参数注解
     * @return 参数绑定策略
     */
    private ArgsHandler handleAnnoQuery(Parameter parameter, Query query) {
        String typeName = "";
        // 判断类型
        short collectionType = 0;
        if (Collection.class.isAssignableFrom(parameter.getType())) {
            if (List.class.isAssignableFrom(parameter.getType())) {
                collectionType = 1;
            } else if (Set.class.isAssignableFrom(parameter.getType())) {
                collectionType = 2;
            }
            // 获取泛型类型
            if (parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    typeName = actualTypeArguments[0].getTypeName();
                }
            }
        } else {
            typeName = parameter.getType().getTypeName();
        }
        ParamInfo info = new ParamInfo(query.value(), null, collectionType, false, null);
        return switch (typeName) {
            case "java.lang.String" -> new ArgsHandler(info, Strategy.QUERY_TYPE_STRING.strategy);
            case "java.lang.Integer", "int" -> new ArgsHandler(info, Strategy.QUERY_TYPE_INTEGER.strategy);
            case "java.lang.Long", "long" -> new ArgsHandler(info, Strategy.QUERY_TYPE_LONG.strategy);
            case "java.lang.Boolean", "boolean" -> new ArgsHandler(info, Strategy.QUERY_TYPE_BOOLEAN.strategy);
            case "java.lang.Double", "double" -> new ArgsHandler(info, Strategy.QUERY_TYPE_DOUBLE.strategy);
            default -> null;
        };
    }

    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        // 创建参数容器
        Object[] args = new Object[this.argsHandlers.length];
        // 绑定参数
        for (int i = 0; i < this.argsHandlers.length; i++) {
            args[i] = this.argsHandlers[i].bind(ctx);
        }
        // 调用方法
        return this.methodHandle.invoke(args);
    }
}
