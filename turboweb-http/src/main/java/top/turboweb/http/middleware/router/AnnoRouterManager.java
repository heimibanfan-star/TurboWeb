package top.turboweb.http.middleware.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboRequestException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.container.RouterContainerInitHelper;
import top.turboweb.http.middleware.router.container.info.RouterDefinition;

import java.util.*;

/**
 * 基于注解声明式的路由管理器
 */
public class AnnoRouterManager extends RouterManager {

    private static final Logger log = LoggerFactory.getLogger(AnnoRouterManager.class);
    // 存储controller的信息
    private final Set<RouterContainerInitHelper.ControllerAttribute> controllers = new HashSet<>();
    private RouterContainer routerContainer;

    @Override
    protected RouterDefinition matchDefinition(HttpContext ctx) {
        // 获取请求方式
        String method = ctx.getRequest().getMethod();
        // 获取请求的路径
        String path = ctx.getRequest().getUri();
        // 数据校验
        if (method == null || path == null || method.isEmpty() || path.isEmpty()) {
            throw new TurboRequestException("request method and request uri is null or empty");
        }
        // 尝试进行精确匹配
        RouterDefinition routerDefinition = routerContainer.exactMatch(method, path);
        if (routerDefinition != null) {
            return routerDefinition;
        }
        // 当精确匹配无法匹配到时进行参数模式匹配
        RouterContainer.TrieMatchResult matchResult = routerContainer.trieMatch(method, path);
        if (matchResult != null) {
            // 解析路径参数
            parsePathVariable(ctx, matchResult);
            return matchResult.definition();
        }
        return null;
    }

    /**
     * 解析路径参数
     *
     * @param ctx              上下文
     * @param trieMatchResult 前缀树匹配结果
     */
    private void parsePathVariable(HttpContext ctx, RouterContainer.TrieMatchResult trieMatchResult) {
        Map<String, String> params = trieMatchResult.params();
        if (params == null) {
            params = new HashMap<>(1);
        }
        ctx.injectPathParam(params);
    }

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
}
