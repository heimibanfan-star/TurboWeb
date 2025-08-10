package top.turboweb.http.middleware.router.info;

import top.turboweb.commons.anno.*;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.session.HttpSession;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 自动绑定参数的路由定义信息
 */
public class AutoBindRouterDefinition implements RouterDefinition {

    private final MethodHandle methodHandle;
    private final Method method;
    private static final Map<Class<?>, BiFunction<HttpContext, Parameter, Object>> paramBindStrategy;

    static {
        paramBindStrategy = Map.of(
                // 路径参数的自动绑定方法
                Param.class, (ctx, param) -> {
                    Param paramAnno = param.getAnnotation(Param.class);
                    // 判断形参的类型
                    return switch (param.getType().getName()) {
                        case "java.lang.String" -> ctx.param(paramAnno.value());
                        case "java.lang.Integer", "int" -> ctx.paramInt(paramAnno.value());
                        case "java.lang.Long", "long" -> ctx.paramLong(paramAnno.value());
                        case "java.lang.Boolean", "boolean" -> ctx.paramBool(paramAnno.value());
                        case "java.lang.Double", "double" -> ctx.paramDouble(paramAnno.value());
                        case "java.time.LocalDate" -> ctx.paramDate(paramAnno.value());
                        default -> null;
                    };
                },
                // 路径参数的自动封装方式
                Query.class, (ctx, param) -> {
                    Query queryAnno = param.getAnnotation(Query.class);
                    return switch (param.getType().getName()) {
                        case "java.lang.String" -> ctx.query(queryAnno.value());
                        case "java.lang.Integer", "int" -> ctx.queryInt(queryAnno.value());
                        case "java.lang.Long", "long" -> ctx.queryLong(queryAnno.value());
                        case "java.lang.Boolean", "boolean" -> ctx.queryBool(queryAnno.value());
                        default -> null;
                    };
                },
                // 将路径参数封装为实体
                QueryModel.class, (ctx, param) -> {
                    QueryModel queryModelAnno = param.getAnnotation(QueryModel.class);
                    // 判断是否开启数据校验
                    if (!queryModelAnno.value()) {
                        return ctx.loadQuery(param.getType());
                    } else {
                        // 判断是否含有校验组
                        Class<?>[] groups = queryModelAnno.groups();
                        return groups.length == 0 ? ctx.loadQuery(param.getType()) : ctx.loadValidQuery(param.getType(), groups);
                    }
                },
                // 将表单参数封装为实体
                FormModel.class, (ctx, param) -> {
                    FormModel formModelAnno = param.getAnnotation(FormModel.class);
                    // 判断是否开启参数校验
                    if (!formModelAnno.value()) {
                        return ctx.loadForm(param.getType());
                    } else {
                        // 获取参数校验组
                        Class<?>[] groups = formModelAnno.groups();
                        return groups.length == 0 ? ctx.loadForm(param.getType()) : ctx.loadValidForm(param.getType(), groups);
                    }
                },
                // 将json参数封装为实体
                JsonModel.class, (ctx, param) -> {
                    JsonModel jsonModelAnno = param.getAnnotation(JsonModel.class);
                    // 判断是否开启参数校验
                    if (!jsonModelAnno.value()) {
                        return ctx.loadJson(param.getType());
                    } else {
                        // 获取参数校验组
                        Class<?>[] groups = jsonModelAnno.groups();
                        return groups.length == 0 ? ctx.loadJson(param.getType()) : ctx.loadValidJson(param.getType(), groups);
                    }
                }
        );
    }

    public AutoBindRouterDefinition(Object instance, Method method) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            methodHandle = lookup.unreflect(method).bindTo(instance).asSpreader(Object[].class, method.getParameterCount());
        } catch (IllegalAccessException e) {
            throw new TurboRouterDefinitionCreateException(e);
        }
        this.method = method;
    }

    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        // 获取当前方法的所有参数
        Parameter[] parameters = method.getParameters();
        // 用于存储参数的值
        Object[] args = new Object[parameters.length];
        // 遍历参数
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType() == HttpContext.class) {
                args[i] = ctx;
            } else if (parameter.getType() == HttpSession.class) {
                args[i] = ctx.httpSession();
            } else if (parameter.getType() == HttpCookieManager.class) {
                args[i] = ctx.cookie();
            } else {
                // 获取所有的注解
                Annotation[] annotations = parameter.getAnnotations();
                // 根据注解查询封装策略
                BiFunction<HttpContext, Parameter, Object> func = null;
                for (Annotation annotation : annotations) {
                    func = paramBindStrategy.get(annotation.annotationType());
                    if (func != null) {
                        break;
                    }
                }
                // 判断是否匹配到指定的策略
                if (func != null) {
                    args[i] = func.apply(ctx, parameter);
                } else {
                    args[i] = null;
                }
            }
        }
        return methodHandle.invoke(args);
    }
}
