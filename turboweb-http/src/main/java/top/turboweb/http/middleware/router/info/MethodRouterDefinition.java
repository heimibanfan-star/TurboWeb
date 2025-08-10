package top.turboweb.http.middleware.router.info;

import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.context.HttpContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 方法路由定义信息
 */
public class MethodRouterDefinition implements RouterDefinition {

    // 方法句柄
    private final MethodHandle methodHandle;

    public MethodRouterDefinition(Object instance, Method method) {
        // 获取所有的方法参数
        Parameter[] parameters = method.getParameters();
        // 判断方法参数是否符合规范
        if (parameters.length != 1) {
            throw new TurboRouterDefinitionCreateException("未开启参数自动封装，方法参数只能有一个HttpContext");
        }
        if (!parameters[0].getType().equals(HttpContext.class)) {
            throw new TurboRouterDefinitionCreateException("未开启参数自动封装，方法参数只能有一个HttpContext");
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            this.methodHandle = lookup.unreflect(method).bindTo(instance);
        } catch (IllegalAccessException e) {
            throw new TurboRouterDefinitionCreateException(e);
        }
    }

    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        return methodHandle.invoke(ctx);
    }
}
