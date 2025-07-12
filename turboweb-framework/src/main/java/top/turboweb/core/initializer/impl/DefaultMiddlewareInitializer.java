package top.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.core.config.HttpServerConfig;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.middleware.aware.ExceptionHandlerMatcherAware;
import top.turboweb.http.middleware.aware.MainClassAware;
import top.turboweb.http.middleware.aware.SessionManagerHolderAware;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.core.initializer.MiddlewareInitializer;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认的中间件初始化器
 */
public class DefaultMiddlewareInitializer implements MiddlewareInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultMiddlewareInitializer.class);
    // 路由管理器
    private RouterManager routerManager;
    // 存储中间件对象
    private final List<Middleware> middlewares = new ArrayList<>();


    @Override
    public void routerManager(RouterManager routerManager) {
        this.routerManager = routerManager;
    }

    @Override
    public void addMiddleware(Middleware... middleware) {
        this.middlewares.addAll(List.of(middleware));
    }

    @Override
    public Middleware init(
        SessionManagerHolder sessionManagerHolder,
        Class<?> mainClass,
        ExceptionHandlerMatcher matcher,
        HttpServerConfig config
    ) {
        Middleware chain = initMiddlewareChain();
        // 执行依赖注入
        initMiddlewareForAware(chain, sessionManagerHolder, matcher, mainClass, config);
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
        for (Middleware middleware : middlewares) {
            ptr.setNext(middleware);
            ptr = middleware;
        }
        ptr.setNext(routerManager);
        return chain;
    }

    /**
     * 进行中间件依赖底层组件的依赖注入
     *
     * @param ptr 头结点的指针
     * @param sessionManagerHolder session代理
     * @param exceptionHandlerMatcher 异常匹配器
     * @param mainClass 主启动类
     * @param config 配置
     */
    private void initMiddlewareForAware(
        Middleware ptr,
        SessionManagerHolder sessionManagerHolder,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        Class<?> mainClass,
        HttpServerConfig config
    ) {
        while (ptr != null) {
            // 判断是否实现Aware
            if (ptr instanceof SessionManagerHolderAware aware) {
                aware.setSessionManagerProxy(sessionManagerHolder);
            }
            if (ptr instanceof ExceptionHandlerMatcherAware aware) {
                aware.setExceptionHandlerMatcher(exceptionHandlerMatcher);
            }
            if (ptr instanceof MainClassAware aware) {
                aware.setMainClass(mainClass);
            }
            ptr = ptr.getNext();
        }
        log.info("中间件依赖注入完成");
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
