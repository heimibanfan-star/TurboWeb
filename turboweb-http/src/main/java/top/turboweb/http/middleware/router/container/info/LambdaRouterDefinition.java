package top.turboweb.http.middleware.router.container.info;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.LambdaHandler;

/**
 * 基于lambda的路由定义信息
 */
public class LambdaRouterDefinition implements RouterDefinition {

    private final LambdaHandler lambdaHandler;

    public LambdaRouterDefinition(LambdaHandler lambdaHandler) {
        this.lambdaHandler = lambdaHandler;
    }

    @Override
    public Object invoke(HttpContext ctx) throws Throwable {
        return lambdaHandler.handle(ctx);
    }
}
