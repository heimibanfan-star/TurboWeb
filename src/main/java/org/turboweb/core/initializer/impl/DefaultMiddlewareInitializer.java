package org.turboweb.core.initializer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.config.ServerParamConfig;
import org.turboweb.core.http.handler.ExceptionHandlerMatcher;
import org.turboweb.core.http.middleware.HttpRouterDispatcherMiddleware;
import org.turboweb.core.http.middleware.Middleware;
import org.turboweb.core.http.middleware.SentinelMiddleware;
import org.turboweb.core.http.middleware.aware.CharsetAware;
import org.turboweb.core.http.middleware.aware.ExceptionHandlerMatcherAware;
import org.turboweb.core.http.middleware.aware.MainClassAware;
import org.turboweb.core.http.middleware.aware.SessionManagerProxyAware;
import org.turboweb.core.http.router.container.RouterContainer;
import org.turboweb.core.http.router.dispatcher.HttpDispatcher;
import org.turboweb.core.http.router.dispatcher.impl.DefaultHttpDispatcher;
import org.turboweb.core.http.router.matcher.RouterMatcher;
import org.turboweb.core.http.router.matcher.impl.DefaultRouterMatcher;
import org.turboweb.core.http.session.SessionManagerProxy;
import org.turboweb.core.initializer.MiddlewareInitializer;
import org.turboweb.utils.init.RouterContainerInitUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认的中间件初始化器
 */
public class DefaultMiddlewareInitializer implements MiddlewareInitializer {

    private static final Logger log = LoggerFactory.getLogger(DefaultMiddlewareInitializer.class);
    // 存储控制器对象
    private final List<Object> controllers = new ArrayList<>();
    // 存储中间件对象
    private final List<Middleware> middlewares = new ArrayList<>();


    @Override
    public void addController(Object... controllers) {
        this.controllers.addAll(List.of(controllers));
    }

    @Override
    public void addMiddleware(Middleware... middleware) {
        this.middlewares.addAll(List.of(middleware));
    }

    @Override
    public Middleware init(
        SessionManagerProxy sessionManagerProxy,
        Class<?> mainClass,
        ExceptionHandlerMatcher matcher,
        ServerParamConfig config
    ) {
        Middleware chain = initMiddlewareChain();
        // 执行依赖注入
        initMiddlewareForAware(chain, sessionManagerProxy, matcher, mainClass, config);
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
        ptr.setNext(getHttpDispatcherMiddleware());
        return chain;
    }

    /**
     * 进行中间件依赖底层组件的依赖注入
     *
     * @param ptr 头结点的指针
     * @param sessionManagerProxy session代理
     * @param exceptionHandlerMatcher 异常匹配器
     * @param mainClass 主启动类
     * @param config 配置
     */
    private void initMiddlewareForAware(
        Middleware ptr,
        SessionManagerProxy sessionManagerProxy,
        ExceptionHandlerMatcher exceptionHandlerMatcher,
        Class<?> mainClass,
        ServerParamConfig config
    ) {
        while (ptr != null) {
            // 判断是否实现Aware
            if (ptr instanceof SessionManagerProxyAware aware) {
                aware.setSessionManagerProxy(sessionManagerProxy);
            }
            if (ptr instanceof ExceptionHandlerMatcherAware aware) {
                aware.setExceptionHandlerMatcher(exceptionHandlerMatcher);
            }
            if (ptr instanceof MainClassAware aware) {
                aware.setMainClass(mainClass);
            }
            if (ptr instanceof CharsetAware aware) {
                aware.setCharset(config.getCharset());
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

    /**
     * 创建路由分发器的中间件
     *
     * @return 路由分发器的中间件
     */
    private HttpRouterDispatcherMiddleware getHttpDispatcherMiddleware() {
        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllers);
        RouterMatcher routerMatcher = new DefaultRouterMatcher(routerContainer);
        HttpDispatcher dispatcher =  new DefaultHttpDispatcher(routerMatcher);
        log.info("http分发器初始化成功");
        return new HttpRouterDispatcherMiddleware(dispatcher);
    }
}
