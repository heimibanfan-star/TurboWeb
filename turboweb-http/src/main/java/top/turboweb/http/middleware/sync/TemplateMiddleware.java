package top.turboweb.http.middleware.sync;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.response.ViewModel;

/**
 * 模型渲染的中间件
 */
public abstract class TemplateMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        Object result = next(ctx);
        // 判断是否是模板渲染
        if (result instanceof ViewModel viewModel) {
            return ctx.html(render(ctx, viewModel));
        }
        return result;
    }

    /**
     * 渲染模板
     * @param ctx 上下文
     * @param viewModel 模型
     * @return 渲染后的html数据
     */
    public abstract String render(HttpContext ctx, ViewModel viewModel);

}
