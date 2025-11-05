package top.turboweb.http.middleware.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.anno.*;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.router.container.DefaultRouterContainer;
import top.turboweb.http.middleware.router.container.PathHelper;
import top.turboweb.http.middleware.router.container.RouterContainer;
import top.turboweb.http.middleware.router.info.AutoBindRouterDefinition;
import top.turboweb.http.middleware.router.info.MethodRouterDefinition;
import top.turboweb.http.middleware.router.info.RouterDefinition;
import top.turboweb.http.middleware.router.info.TrieRouterInfo;
import top.turboweb.http.middleware.router.info.autobind.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * {@code AnnoRouterManager} 是基于注解（Annotation）的声明式路由管理器，
 * 负责自动扫描、解析并注册控制器（Controller）中的路由方法。
 * </p>
 *
 * <p>
 * 它是 {@link RouterManager} 的具体实现，通过读取控制器类和方法上的路由注解
 * （如 {@link Get}、{@link Post}、{@link Put}、{@link Delete}、{@link Patch} 等），
 * 构建可执行的路由映射表，并注册到 {@link RouterContainer} 中。
 * </p>
 *
 * <h3>主要特性</h3>
 * <ul>
 *   <li>支持声明式注解路由定义。</li>
 *   <li>支持路径前缀（{@code pathPrefix}）统一管理。</li>
 *   <li>支持参数自动绑定（{@code autoBind = true}）。</li>
 *   <li>线程安全初始化（基于 {@link ReentrantLock}）。</li>
 *   <li>支持自定义参数解析器扩展。</li>
 * </ul>
 *
 * <h3>生命周期说明</h3>
 * <ul>
 *   <li>启动阶段调用 {@link #addController(Object)} 注册控制器实例。</li>
 *   <li>初始化阶段执行 {@link #init(Middleware)} 完成路由扫描与注册。</li>
 *   <li>运行时由上层 {@link Middleware} 负责请求分发。</li>
 * </ul>
 */
public class AnnoRouterManager extends RouterManager {

    private static final Logger log = LoggerFactory.getLogger(AnnoRouterManager.class);
    // 存储controller的信息
    private final Set<ControllerAttribute> controllers = new HashSet<>();
    private final RouterContainer routerContainer = new DefaultRouterContainer();
    private final boolean autoBind;
    private boolean isInit = false;
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayList<ParameterInfoParser> parsers = new ArrayList<>();
    private final String pathPrefix;

    {
        parsers.add(new InternTypeParamInfoParser());
        parsers.add(new RestParameterInfoParser());
        parsers.add(new QueryParameterInfoParser());
        parsers.add(new UploadParameterInfoParser());
        parsers.add(new QueryModelParameterInfoParser());
        parsers.add(new FormModelParameterInfoParser());
        parsers.add(new JsonModelParameterInfoParser());
    }

    public static class ControllerAttribute {
        private final Object instance;
        private final Class<?> clazz;

        public ControllerAttribute(Object instance, Class<?> clazz) {
            this.instance = instance;
            this.clazz = clazz;
        }
    }

    public AnnoRouterManager() {
        this("", false);
    }

    public AnnoRouterManager(boolean autoBind) {
        this("", autoBind);
    }

    public AnnoRouterManager(String pathPrefix) {
        this(pathPrefix, false);
    }

    /**
     * 创建基于注解的路由管理器。
     *
     * @param pathPrefix 路由前缀（如 "/api"）
     * @param autoBind   是否启用自动参数绑定
     */
    public AnnoRouterManager(String pathPrefix, boolean autoBind) {
        if (pathPrefix == null || "/".equals(pathPrefix)) {
            pathPrefix = "";
        } else {
            pathPrefix = pathPrefix.trim();
        }
        if (pathPrefix.isEmpty()) {
            pathPrefix = "/" + pathPrefix;
        }
        if (pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }
        this.pathPrefix = pathPrefix;
        this.autoBind = autoBind;
    }

    /**
     * 注册一个控制器实例。
     *
     * @param controller 控制器对象（包含注解的类实例）
     * @return 当前 {@link AnnoRouterManager} 实例
     */
    public AnnoRouterManager addController(Object controller) {
        Objects.requireNonNull(controller);
        // 创建controller的信息
        ControllerAttribute controllerAttribute = new ControllerAttribute(
                controller, controller.getClass()
        );
        // 添加到容器中
        controllers.add(controllerAttribute);
        return this;
    }

    /**
     * 注册控制器，并显式指定其原始类。
     * <p>用于代理类、动态增强类等情况。</p>
     *
     * @param controller  控制器实例
     * @param originClass 控制器原始类型
     * @return 当前 {@link AnnoRouterManager} 实例
     */
    public AnnoRouterManager addController(Object controller, Class<?> originClass) {
        Objects.requireNonNull(controller);
        Objects.requireNonNull(originClass);
        // 创建controller的信息
        ControllerAttribute controllerAttribute = new ControllerAttribute(
                controller, originClass
        );
        // 添加到容器中
        controllers.add(controllerAttribute);
        return this;
    }

    @Override
    public void init(Middleware chain) {
        super.init(chain);
        if (isInit) {
            return;
        }
        lock.lock();
        try {
            if (isInit) {
                return;
            }
            initRouterContainer();
            isInit = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 在解析链末尾添加参数解析器。
     *
     * @param parser 参数解析器实例
     * @return 当前 {@link RouterManager} 实例
     * @throws IllegalStateException 当解析器类型重复时抛出
     */
    public RouterManager addParameterParserLast(ParameterInfoParser parser) {
        for (ParameterInfoParser p : parsers) {
            if (p.getClass() == parser.getClass()) {
                throw new IllegalStateException("Duplicate parameter parser:" + parser.getClass().getName());
            }
        }
        parsers.addLast(parser);
        return this;
    }

    /**
     * 在解析链首部添加参数解析器。
     *
     * @param parser 参数解析器实例
     * @return 当前 {@link RouterManager} 实例
     * @throws IllegalStateException 当解析器类型重复时抛出
     */
    public RouterManager addParameterParserFirst(ParameterInfoParser parser) {
        for (ParameterInfoParser p : parsers) {
            if (p.getClass() == parser.getClass()) {
                throw new IllegalStateException("Duplicate parameter parser:" + parser.getClass().getName());
            }
        }
        parsers.addFirst(parser);
        return this;
    }

    /**
     * 替换或插入指定类型位置的解析器。
     *
     * @param type   要替换的解析器类型
     * @param parser 新解析器实例
     * @return 当前 {@link RouterManager} 实例
     */
    public RouterManager addParameterParserAt(Class<? extends ParameterInfoParser> type, ParameterInfoParser parser) {
        int index = -1;
        for (ParameterInfoParser p : parsers) {
            index++;
            if (p.getClass() == type) {
                break;
            }
        }
        if (index == -1) {
            parsers.addLast(parser);
        } else {
            parsers.remove(index);
            parsers.add(index, parser);
        }
        return this;
    }

    @Override
    protected RouterContainer getRouterContainer() {
        return this.routerContainer;
    }

    /**
     * 初始化路由容器
     */
    private void initRouterContainer() {
        for (ControllerAttribute attribute : controllers) {
            // 获取类的字节码对象
            Class<?> aClass = Objects.requireNonNullElseGet(attribute.clazz, attribute.instance::getClass);
            // 存储实例信息
            routerContainer.getControllerInstances().put(aClass, attribute.instance);
            // 获取所有的方法信息
            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                // 过滤掉不合格的方法
                if (!Modifier.isPublic(method.getModifiers()) || method.getAnnotations().length == 0) {
                    continue;
                }
                // 初始化路由定义信息
                initRouterDefinition(method, aClass);
            }
        }
    }

    /**
     * 初始化路由定义信息
     *
     * @param method 方法信息
     * @param clazz 类信息
     */
    private void initRouterDefinition(Method method, Class<?> clazz) {
        // 获取前缀路径
        String prePath = "";
        if (clazz.isAnnotationPresent(Route.class)) {
            prePath = clazz.getAnnotation(Route.class).value();
        } else if (clazz.isAnnotationPresent(RequestPath.class)) {
            prePath = clazz.getAnnotation(RequestPath.class).value();
        }
        // 解析方法注解
        if (method.isAnnotationPresent(Get.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Get.class).value());
            doInitRouterDefinition(method, path);
        } else if (method.isAnnotationPresent(Post.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Post.class).value());
            doInitRouterDefinition(method, path);
        } else if (method.isAnnotationPresent(Put.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Put.class).value());
            doInitRouterDefinition(method, path);
        } else if (method.isAnnotationPresent(Patch.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Patch.class).value());
            doInitRouterDefinition(method, path);
        } else if (method.isAnnotationPresent(Delete.class)) {
            String path = PathHelper.mergePath(prePath, method.getAnnotation(Delete.class).value());
            doInitRouterDefinition(method, path);
        }
    }

    /**
     * 初始化路由定义信息
     *
     * @param method 方法信息
     * @param path 路径信息
     */
    private void doInitRouterDefinition(Method method, String path) {
        // 校验路径是否合法
        if (path.contains("*") || path.contains("?")) {
            throw new TurboRouterDefinitionCreateException("path is not allowed to contain * or ? ");
        }
        // 获取当前实例对象
        Object instance = routerContainer.getControllerInstances().get(method.getDeclaringClass());
        // 创建路由定义信息
        RouterDefinition routerDefinition;
        if (autoBind) {
            routerDefinition = new AutoBindRouterDefinition(parsers, instance, method);
        } else {
            routerDefinition = new MethodRouterDefinition(instance, method);
        }
        parsePathAndSaveDefinition(method, path, routerDefinition);
    }

    /**
     * 解析路径并保存路由定义信息
     *
     * @param method 方法信息
     * @param path 路径信息
     * @param definition 路由定义信息
     */
    private void parsePathAndSaveDefinition(Method method, String path, RouterDefinition definition) {
        String type;
        if (method.isAnnotationPresent(Get.class)) {
            type = "GET";
        } else if (method.isAnnotationPresent(Post.class)) {
            type = "POST";
        } else if (method.isAnnotationPresent(Put.class)) {
            type = "PUT";
        } else if (method.isAnnotationPresent(Delete.class)) {
            type = "DELETE";
        } else if (method.isAnnotationPresent(Patch.class)) {
            type = "PATCH";
        } else {
            throw new TurboRouterDefinitionCreateException("未知的路由类型");
        }
        // 拼接路径前缀
        path = pathPrefix + path;
        int index = path.indexOf("{");
        if (index != -1) {
            // 根据请求方式获取前缀树
            TrieRouterInfo trieRouterInfo = routerContainer.getTrieRouterInfo();
            // 添加到前缀树中
            trieRouterInfo.addRouter(type, path, definition);
        }
        routerContainer.getExactRouterInfo().addRouter(type, path, definition);
    }
}
