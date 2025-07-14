package top.turboweb.http.middleware.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.container.RouterContainerInitHelper;

import java.util.*;

/**
 * 基于注解声明式的路由管理器
 */
public class AnnoRouterManager extends RouterManager {

    private static final Logger log = LoggerFactory.getLogger(AnnoRouterManager.class);
    // 存储controller的信息
    private final Set<RouterContainerInitHelper.ControllerAttribute> controllers = new HashSet<>();
    private RouterContainer routerContainer;

    /**
     * 添加controller
     *
     * @param controller controller对象
     * @return this
     */
    public AnnoRouterManager addController(Object controller) {
        Objects.requireNonNull(controller);
        // 创建controller的信息
        RouterContainerInitHelper.ControllerAttribute controllerAttribute = new RouterContainerInitHelper.ControllerAttribute(
                controller, controller.getClass()
        );
        // 添加到容器中
        controllers.add(controllerAttribute);
        return this;
    }

    /**
     * 添加controller
     *
     * @param controller controller对象
     * @param originClass controller的源类
     * @return this
     */
    public AnnoRouterManager addController(Object controller, Class<?> originClass) {
        Objects.requireNonNull(controller);
        Objects.requireNonNull(originClass);
        // 创建controller的信息
        RouterContainerInitHelper.ControllerAttribute controllerAttribute = new RouterContainerInitHelper.ControllerAttribute(
                controller, originClass
        );
        // 添加到容器中
        controllers.add(controllerAttribute);
        return this;
    }

    @Override
    public void init(Middleware chain) {
        this.routerContainer = RouterContainerInitHelper.initContainer(controllers);
        log.info("AnnoRouterManager init success");
    }

    @Override
    protected RouterContainer getRouterContainer() {
        return this.routerContainer;
    }
}
