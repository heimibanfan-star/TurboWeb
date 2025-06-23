package top.turboweb.http.router.container.info;

import top.turboweb.http.context.HttpContext;

import java.lang.invoke.MethodHandle;

/**
 * 方法路由定义信息
 */
public class MethodRouterDefinition implements RouterDefinition {

    // 方法句柄
    private final MethodHandle methodHandle;

    public MethodRouterDefinition(MethodHandle methodHandle) {
        this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        return methodHandle.invoke(ctx);
    }
}
