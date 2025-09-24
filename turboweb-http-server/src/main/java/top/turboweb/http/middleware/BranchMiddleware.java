package top.turboweb.http.middleware;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.http.context.HttpContext;

import java.util.*;

/**
 * 分支中间件
 */
public abstract class BranchMiddleware extends Middleware {

    private final Map<String, LinkedHashSet<Middleware>> middlewareMap = new HashMap<>();
    private final Map<String, Middleware> middlewareChain = new HashMap<>();

    private static class MergeMiddleware extends Middleware {

        @Override
        public Object invoke(HttpContext ctx) {
            return next(ctx);
        }
    }

    @Override
    public Object invoke(HttpContext ctx) {
        String branchKey = getBranchKey(ctx);
        if (!middlewareChain.containsKey(branchKey)) {
            throw new TurboRouterException("The branch " + branchKey + " does not exist", TurboRouterException.ROUTER_NOT_MATCH);
        }
        return middlewareChain.get(branchKey).invoke(ctx);
    }

    /**
     * 添加中间件
     *
     * @param key           分支的key
     * @param middleware    中间件
     * @return this
     */
    public BranchMiddleware addMiddleware(String key, Middleware middleware) {
        Objects.requireNonNull(key, "The key cannot be null");
        Objects.requireNonNull(middleware, "The middleware cannot be null");
        if (!middlewareMap.containsKey(key)) {
            middlewareMap.put(key, new LinkedHashSet<>());
        }
        LinkedHashSet<Middleware> middlewares = middlewareMap.get(key);
        // 判断是否存在重复的
        if (middlewares.contains(middleware)) {
            throw new TurboServerInitException("Duplicate middleware " + middleware + "appears in branch " + key);
        }
        middlewares.add(middleware);
        return this;
    }

    /**
     * 获取分支的key
     *
     * @param ctx 请求的上下文
     * @return 分支的key
     */
    protected abstract String getBranchKey(HttpContext ctx);

    @Override
    public void init(Middleware chain) {
        // 创建聚合中间件
        MergeMiddleware mergeMiddleware = new MergeMiddleware();
        mergeMiddleware.setNext(getNext());
        // 依次组装中间件
        for (Map.Entry<String, LinkedHashSet<Middleware>> entry : middlewareMap.entrySet()) {
            String key = entry.getKey();
            LinkedHashSet<Middleware> middlewares = entry.getValue();
            if (middlewares.isEmpty()) {
                middlewareChain.put(key, mergeMiddleware);
            } else {
                // 组装中间件
                Iterator<Middleware> iterator = middlewares.iterator();
                // 获取第一个中间件
                Middleware firstMiddleware = iterator.next();
                Middleware lastMiddleware = firstMiddleware;
                while (iterator.hasNext()) {
                    Middleware nextMiddleware = iterator.next();
                    lastMiddleware.setNext(nextMiddleware);
                    lastMiddleware = nextMiddleware;
                }
                // 拼接聚合中间件
                lastMiddleware.setNext(mergeMiddleware);
                middlewareChain.put(key, firstMiddleware);
            }
        }
    }
}
