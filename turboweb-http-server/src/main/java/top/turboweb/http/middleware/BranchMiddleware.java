package top.turboweb.http.middleware;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.http.context.HttpContext;

import java.util.*;

/**
 * <p>
 * 分支中间件（BranchMiddleware）用于根据上下文动态选择不同的中间件执行链。
 * 每个分支（branch）对应一组独立的中间件链，可在初始化时通过 {@link #addMiddleware(String, Middleware)} 进行注册。
 * </p>
 *
 * <p>
 * 当请求到达时，{@link #invoke(HttpContext)} 方法会根据上下文计算出分支键（branchKey），
 * 并调用对应分支链路的第一个中间件开始执行。
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>多租户请求分流</li>
 *     <li>按业务模块或路由规则选择不同中间件链</li>
 *     <li>条件式管线（Conditional Pipeline）</li>
 * </ul>
 *
 * <p>
 * 生命周期说明：
 * <ul>
 *     <li>在服务器初始化阶段，所有分支中间件应调用 {@link #init(Middleware)} 组装中间件链。</li>
 *     <li>在运行时，根据 {@link #getBranchKey(HttpContext)} 获取对应链路执行。</li>
 * </ul>
 * </p>
 *
 */
public abstract class BranchMiddleware extends Middleware {

    /**
     * 存放每个分支对应的中间件集合。
     * <p>使用 {@link LinkedHashSet} 保证中间件的添加顺序。</p>
     * <p>key：分支标识；value：该分支下的中间件集合。</p>
     */
    private final Map<String, LinkedHashSet<Middleware>> middlewareMap = new HashMap<>();

    /**
     * 每个分支对应的完整中间件执行链。
     * <p>key：分支标识；value：已组装完成的首个中间件实例。</p>
     */
    private final Map<String, Middleware> middlewareChain = new HashMap<>();

    /**
     * <p>
     * 聚合（终止）中间件，用于在每个分支链末尾拼接。
     * 其作用是继续执行主链的下一个中间件。
     * </p>
     */
    private static class MergeMiddleware extends Middleware {

        @Override
        public Object invoke(HttpContext ctx) {
            return next(ctx);
        }
    }

    /**
     * 执行中间件逻辑。
     * <p>根据上下文计算分支键，执行对应分支的中间件链。</p>
     *
     * @param ctx HTTP 请求上下文
     * @return 处理结果对象
     * @throws TurboRouterException 当分支不存在时抛出
     */
    @Override
    public Object invoke(HttpContext ctx) {
        String branchKey = getBranchKey(ctx);
        if (!middlewareChain.containsKey(branchKey)) {
            throw new TurboRouterException("The branch " + branchKey + " does not exist", TurboRouterException.ROUTER_NOT_MATCH);
        }
        return middlewareChain.get(branchKey).invoke(ctx);
    }

    /**
     * 向指定分支添加中间件。
     * <p>若该分支不存在，将自动创建；若中间件重复，则抛出异常。</p>
     *
     * @param key        分支标识（不可为 {@code null}）
     * @param middleware 要添加的中间件实例（不可为 {@code null}）
     * @return 当前 {@link BranchMiddleware} 实例，支持链式调用
     * @throws NullPointerException         当 key 或 middleware 为空时抛出
     * @throws TurboServerInitException     当同一分支中重复添加相同中间件时抛出
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
     * 获取当前请求所属的分支键（branchKey）。
     * <p>子类应根据 {@link HttpContext} 的内容（例如路径、Header、参数等）返回唯一键。</p>
     *
     * @param ctx HTTP 请求上下文
     * @return 分支键（不可为 {@code null}）
     */
    protected abstract String getBranchKey(HttpContext ctx);

    /**
     * 初始化阶段调用，用于组装每个分支的中间件链。
     * <p>
     * 每个分支链的最后一个中间件都会连接到 {@link MergeMiddleware}，
     * 以便执行主链后续的中间件。
     * </p>
     *
     * @param chain 主链的下一个中间件
     */
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
