package top.turboweb.http.middleware.router;

import top.turboweb.commons.exception.TurboRouterException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.RouterContainer;

import java.util.*;

/**
 * <p>
 * {@code VersionRouterManager} 是一个基于版本号（version）的多路由管理器，
 * 用于在同一服务中根据请求的版本信息动态选择对应的 {@link RouterManager} 进行请求分发。
 * </p>
 *
 * <p>
 * 它是 {@link RouterManager} 的抽象实现，主要职责是：
 * <ul>
 *     <li>维护多个版本对应的 {@link RouterManager} 实例。</li>
 *     <li>在请求到达时，根据 {@link HttpContext} 中的版本信息选择合适的路由管理器。</li>
 *     <li>防止循环引用（即禁止嵌套添加另一个 {@code VersionRouterManager}）。</li>
 * </ul>
 * </p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>API 多版本控制（如 /v1、/v2、/v3 等）。</li>
 *     <li>灰度发布或兼容旧版接口时的版本分流。</li>
 *     <li>根据客户端版本选择不同的业务逻辑路由。</li>
 * </ul>
 *
 * <p>
 * 框架初始化阶段应调用 {@link #addRouterManager(String, RouterManager)} 注册各版本的路由管理器。
 * 子类需要实现 {@link #getRouterManager(HttpContext, Managers)} 来定义版本选择逻辑，
 * 例如通过请求路径、Header 或 Query 参数来确定版本。
 * </p>
 *
 */
public abstract class VersionRouterManager extends RouterManager{

    /**
     * <p>
     * 内部版本管理器集合类，用于保存各版本对应的 {@link RouterManager} 实例。
     * </p>
     *
     * <p>设计特点：</p>
     * <ul>
     *     <li>禁止嵌套添加 {@code VersionRouterManager}（防止无限递归嵌套）。</li>
     *     <li>版本唯一：相同版本号不允许重复注册。</li>
     * </ul>
     */
    protected final static class Managers {
        private final Map<String, RouterManager> routers = new HashMap<>();

        /**
         * 添加一个版本对应的路由管理器。
         *
         * @param version       版本号（如 "v1"、"v2"）
         * @param routerManager 路由管理器实例
         * @throws IllegalArgumentException 当尝试添加另一个 {@code VersionRouterManager} 时抛出
         * @throws TurboRouterException     当版本号重复注册时抛出
         */
        private void addRouterManager(String version, RouterManager routerManager) {
            // 禁止添加版本控制路由管理器
            if (routerManager instanceof VersionRouterManager) {
                throw new IllegalArgumentException("Cannot add a VersionRouterManager to a VersionRouterManager");
            }
            // 判断版本是否重复
            if (routers.containsKey(version)) {
                throw new TurboRouterException("The version of RouterManager is repeated: " + version, TurboRouterException.ROUTER_REPEAT);
            }
            // 添加路由管理器
            routers.put(version, routerManager);
        }

        /**
         * 根据版本号获取对应的 {@link RouterManager}。
         *
         * @param version 版本号
         * @return 对应的路由管理器；如果不存在则返回 {@code null}
         */
        public RouterManager getRouterManager(String version) {
            return routers.get(version);
        }

        /**
         * 获取当前已注册的所有版本号集合。
         *
         * @return 版本号集合（副本）
         */
        public Set<String> getVersions() {
            return new HashSet<>(routers.keySet());
        }
    }

    private final Managers managers = new Managers();


    /**
     * 注册指定版本号对应的 {@link RouterManager}。
     *
     * @param version       版本号（如 "v1"、"v2"）
     * @param routerManager 路由管理器实例
     * @throws IllegalArgumentException 当添加的实例为 {@code VersionRouterManager} 时抛出
     * @throws TurboRouterException     当版本号重复注册时抛出
     */
    public void addRouterManager(String version, RouterManager routerManager) {
        managers.addRouterManager(version, routerManager);
    }

    /**
     * 执行当前版本对应的路由逻辑。
     * <p>
     * 该方法会调用子类实现的 {@link #getRouterManager(HttpContext, Managers)}，
     * 根据请求上下文确定版本号，并找到对应的 {@link RouterManager}。
     * 若未找到匹配版本，将抛出 {@link TurboRouterException}。
     * </p>
     *
     * @param ctx HTTP 请求上下文
     * @return 路由处理结果
     * @throws TurboRouterException 当找不到对应版本的 {@link RouterManager} 时抛出
     */
    @Override
    public final Object invoke(HttpContext ctx) {
        RouterManager routerManager = getRouterManager(ctx, managers);
        if (routerManager == null) {
            throw new TurboRouterException("The version of RouterManager is not found: " + ctx.query("version"), TurboRouterException.ROUTER_NOT_MATCH);
        }
        return routerManager.invoke(ctx);
    }

    /**
     * 获取当前请求对应的 {@link RouterManager}。
     * <p>子类可根据请求参数、Header、路径等逻辑来决定版本选择。</p>
     *
     * @param context  请求上下文
     * @param managers 已注册的版本管理器集合
     * @return 匹配的路由管理器；若无匹配可返回 {@code null}
     */
    protected abstract RouterManager getRouterManager(HttpContext context, Managers managers);

    /**
     * 返回路由容器实例。
     * <p>
     * 在版本路由管理器中，该方法被禁用并固定返回 {@code null}，
     * 因为版本管理器自身不直接持有 {@link RouterContainer}，
     * 而是通过内部子 {@link RouterManager} 实例进行具体路由管理。
     * </p>
     *
     * @return 始终返回 {@code null}
     */
    @Override
    protected final RouterContainer getRouterContainer() {
        return null;
    }

    @Override
    public void init(Middleware chain) {
        // 初始化所有路由管理器
        Set<String> versions = this.managers.getVersions();
        for (String version : versions) {
            RouterManager routerManager = this.managers.getRouterManager(version);
            routerManager.init(chain);
        }
    }
}
