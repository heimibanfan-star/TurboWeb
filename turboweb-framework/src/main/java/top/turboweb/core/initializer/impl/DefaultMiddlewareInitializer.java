package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboServerInitException;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.core.initializer.MiddlewareInitializer;

import java.util.*;

/**
 * 默认的中间件初始化器
 */
public class DefaultMiddlewareInitializer implements MiddlewareInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultMiddlewareInitializer.class);
    // 路由管理器
    private RouterManager routerManager;
    // 存储中间件对象
    private final Set<Middleware> middlewareSet = new LinkedHashSet<>();


    @Override
    public void routerManager(RouterManager routerManager) {
        this.routerManager = routerManager;
    }

    @Override
    public void addMiddleware(Middleware... middlewares) {
        for (Middleware middleware : middlewares) {
            Objects.requireNonNull(middleware, "The middleware cannot be null");
            // 判断是否存在重复的中间件
            if (this.middlewareSet.contains(middleware)) {
                throw new TurboServerInitException("The middleware is repeated: " + middleware);
            }
            // 将中间件添加到最后
            this.middlewareSet.add(middleware);
        }
    }

    @Override
    public Middleware init() {
        Middleware chain = initMiddlewareChain();
        // 执行中间件的初始化方法
        doMiddlewareChainInit(chain);
        // 锁定中间件
        doLockMiddleware(chain);
        return chain;
    }

    /**
     * 初始化中间件的结构
     *
     * @return 中间件的头结点
     */
    private Middleware initMiddlewareChain() {
        Middleware chain = new SentinelMiddleware();
        Middleware ptr = chain;
        for (Middleware middleware : middlewareSet) {
            ptr.setNext(middleware);
            ptr = middleware;
        }
        ptr.setNext(routerManager);
        return chain;
    }

    /**
     * 执行中间件的初始化方法
     */
    private void doMiddlewareChainInit(Middleware chain) {
        Middleware ptr = chain;
        while (ptr != null) {
            ptr.init(chain);
            ptr = ptr.getNext();
        }
        log.info("中间件初始化方法执行完成");
    }

    /**
     * 锁定中间件
     */
    private void doLockMiddleware(Middleware chain) {
        Middleware ptr = chain;
        while (ptr != null) {
            ptr.lockMiddleware();
            ptr = ptr.getNext();
        }
        log.info("中间件锁定完成");
    }
}
