package org.turbo.web.core.http.middleware;

import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.response.ViewModel;

/**
 * 模型渲染的中间件
 */
public abstract class TemplateMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        Object result = ctx.doNext();
        // 判断是否是模板渲染
        if (result instanceof ViewModel viewModel) {
            return render(ctx, viewModel);
        }
        return result;
    }

    /**
     * 渲染模板
     * @param ctx 上下文
     * @param viewModel 模型
     * @return 渲染后的响应
     */
    public abstract HttpInfoResponse render(HttpContext ctx, ViewModel viewModel);

}
