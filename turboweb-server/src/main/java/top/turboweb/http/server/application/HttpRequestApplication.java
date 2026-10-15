package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.common.utils.resource.ScopedResource;
import top.turboweb.concurrent.thread.ElasticThreadPool;
import top.turboweb.concurrent.thread.LimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.concurrent.thread.UnlimitedVirtualThreadResource;
import top.turboweb.concurrent.thread.VirtualThreadResource;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.session.DefaultSessionManagerHolder;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.core.session.SessionManager;
import top.turboweb.http.core.session.SessionManagerHolder;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.orchestrator.RequestOrchestrator;
import top.turboweb.http.orchestrator.ResponseWriterManager;
import top.turboweb.http.orchestrator.ResultConverterManager;
import top.turboweb.http.router.RouteHandlerManager;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.server.handler.HttpRequestSchedulerChannelHandler;
import top.turboweb.http.server.handler.RequestSerializerChannelHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * http请求应用，服务器与业务装配逻辑之间的桥梁。
 * <p>
 * 对外只暴露 {@link #configure(ChannelPipeline)}：每条新连接建立时向pipeline尾部追加
 * 请求串行化处理器（可选，连接级新实例）与http请求调度处理器（无状态单例，跨连接复用）；
 * 对内通过 {@link Builder} 在构建期一次性完成请求编排器的装配：串联中间件链（业务中间件
 * 按注册顺序，存在路由处理器管理器时在其后追加路由调用中间件为链尾）、按 {@link HttpConfig}
 * 创建磁盘IO线程池与虚拟线程资源、未由调用方传入的组件补全默认实例，按八组件装定
 * {@link RequestOrchestrator} 后接入调度处理器。
 * <p>
 * 本实例装配完成后不再持有可变状态，可被多个worker线程并发调用。
 * <p>
 * 装配期创建的生命周期资源不在本类中逐个持有，而是统一登记进本应用持有的
 * {@link ScopedResource} 资源作用域：中间件链的销毁适配器（沿链头到链尾调用各中间件的destroy）
 * 先登记，磁盘IO线程池后登记，关闭时由 {@link Application#close()} 按登记顺序
 * 释放整个作用域。服务器关闭时会统一触发，调用方也可对应用实例自行try-with-resources。
 *
 * <pre>{@code
 * HttpRequestApplication application = new HttpRequestApplication.Builder()
 *         .routeHandlerManager(routeHandlerManager)
 *         .config(HttpConfig.builder()
 *                 .serializePerConnection(true)
 *                 .sessionMaxNotUseTime(1800000)
 *                 .build())
 *         .build();
 * }</pre>
 */
public class HttpRequestApplication implements Application {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(HttpRequestApplication.class);

    /**
     * 请求任务虚拟线程的命名前缀，创建虚拟线程资源时透传给线程工厂
     */
    private static final String VIRTUAL_THREAD_NAME = "turboweb-http-handler";

    /**
     * 是否开启连接级请求串行化（来源为 {@link HttpConfig#isSerializePerConnection()}），
     * 开启后每条连接的pipeline中先加入 {@link RequestSerializerChannelHandler}，
     * 保证同一连接的请求按到达顺序被处理
     */
    private final boolean requestSerial;

    /**
     * http请求调度处理器，构建期装配好的无状态单例（{@code @Sharable}），
     * 负责把请求转交给内部 {@link RequestOrchestrator} 执行编排流程
     */
    private final HttpRequestSchedulerChannelHandler httpRequestSchedulerHandler;

    /**
     * 应用的资源作用域：装配期由 {@link Builder#build()} 创建的磁盘IO线程池、中间件链销毁适配器
     * 等可关闭资源全部登记于此，关闭时由 {@link Application#close()} 统一释放，
     * 本类无须持有个别资源的引用
     */
    private final ScopedResource scopedResource;

    public HttpRequestApplication(boolean requestSerial,
                                  HttpRequestSchedulerChannelHandler httpRequestSchedulerHandler,
                                  ScopedResource scopedResource) {
        this.requestSerial = requestSerial;
        this.httpRequestSchedulerHandler = httpRequestSchedulerHandler;
        this.scopedResource = Objects.requireNonNull(scopedResource, "The resource scope must not be null");
    }


    /**
     * 每条新连接建立时被调用一次，向pipeline尾部追加应用级处理器。
     * 串行化处理器有连接级状态必须每次新建，调度处理器为单例直接复用
     */
    @Override
    public void configure(ChannelPipeline pipeline) {
        if (this.requestSerial) {
            pipeline.addLast(new RequestSerializerChannelHandler());
        }
        pipeline.addLast(httpRequestSchedulerHandler);
        log.debug("Http application handlers assembled for connection [{}], request serialization: {}",
                pipeline.channel().id().asShortText(), this.requestSerial);
    }

    /**
     * 获取本应用持有的资源作用域，关闭逻辑由 {@link Application#close()} 的默认实现
     * 按作用域统一释放，本类无须自行编写
     */
    @Override
    public ScopedResource getScopedResource() {
        return this.scopedResource;
    }

    /**
     * 沿中间件链销毁全部中间件：自链头（哨兵）起顺序遍历收集链上的中间件，
     * 包含追加在链尾的路由调用中间件，再逐个调用 {@link Middleware#destroy()}，
     * 遍历顺序与装配期的init一致；单个中间件的销毁异常只记录日志，不中断后续中间件的销毁。
     * <p>
     * 装配期把本方法包成 {@link AutoCloseable} 登记进应用的资源作用域，
     * 因此应用关闭时它会先于磁盘IO线程池的关闭被执行
     *
     * @param middlewareChainHead 中间件链链头，为null时为空操作
     */
    private static void destroyMiddlewareChain(Middleware middlewareChainHead) {
        if (middlewareChainHead == null) {
            return;
        }
        List<Middleware> chain = new ArrayList<>();
        for (Middleware current = middlewareChainHead; current != null; current = current.getNext()) {
            chain.add(current);
        }
        for (Middleware middleware : chain) {
            try {
                middleware.destroy();
            } catch (Exception e) {
                log.error("Error destroying middleware: {}", middleware.getClass().getName(), e);
            }
        }
        log.info("Middleware chain destroyed, {} middleware components in total", chain.size());
    }

    /**
     * {@link HttpRequestApplication} 的建造者：持有应用装配所需的全部组件对象
     * （业务中间件、路由处理器管理器、异常处理器管理器、session管理器、json序列化器、
     * cors配置、结果转化器管理器、响应写出器管理器），纯参数配置（串行化开关、session回收参数、
     * 虚拟线程限流参数）全部由 {@link HttpConfig} 承载；组件均带默认值，按需覆盖即可，
     * 未传入者构建时补全默认实例，装配完成的编排器组件均不可为null。
     * <p>
     * {@link #build()} 按以下顺序完成装配：
     * 串联并初始化中间件链 -> 按 {@link HttpConfig} 的 diskOpeThread* 参数创建磁盘IO线程池 ->
     * 按限流参数创建虚拟线程资源（启用限流为 {@link LimitedVirtualThreadResource}，
     * 否则为 {@link UnlimitedVirtualThreadResource}）-> 补全未传入的组件默认值 ->
     * 按八组件装配 {@link RequestOrchestrator} -> 装配http请求调度处理器。
     * 装配出的中间件链销毁适配器先登记、磁盘IO线程池后登记进应用的
     * {@link ScopedResource} 资源作用域，由应用在关闭时按登记顺序统一释放。
     * <p>
     * Builder 为一次性装配载体：{@link #build()} 仅允许调用一次，重复调用在装配开始前抛出
     * {@link IllegalStateException}。
     */
    public static class Builder {

        /**
         * 业务中间件集合，按注册顺序串联成链，路由调用中间件（若挂有路由处理器管理器）在装配时追加在链尾
         */
        private final List<Middleware> middlewares = new ArrayList<>();
        /**
         * 异常处理器管理器，未传入时默认为登记空处理器集合的管理器
         */
        private ExceptionHandlerManager exceptionHandlerManager = null;
        /**
         * 路由处理器管理器，传null表示无路由：装配时不创建路由调用中间件
         */
        private RouteHandlerManager routeHandlerManager = null;
        /**
         * session管理器，默认内存实现 {@link MemorySessionManager}，装配时与session参数打包为会话管理器持有者
         */
        private SessionManager sessionManager = new MemorySessionManager();
        /**
         * json序列化器，默认Jackson实现，用于响应体序列化与session数据转换；
         * 未显式设置结果转化器管理器时，用它构建默认结果转化器管理器，使两者持有同一实例
         */
        private JsonSerializer jsonSerializer = new JacksonJsonSerializer();
        /**
         * cors配置，未传入时默认 {@link HttpCorsConfig} 的通配配置
         */
        private HttpCorsConfig corsConfig = null;
        /**
         * 结果转化器管理器，未显式设置时构建期以json序列化器补全 {@link ResultConverterManager}
         */
        private ResultConverterManager resultConverterManager = null;
        /**
         * 响应写出器管理器，未显式设置时构建期以磁盘IO线程池与空自定义写出器补全 {@link ResponseWriterManager}
         */
        private ResponseWriterManager responseWriterManager = null;
        /**
         * 应用纯参数配置（串行化开关、session回收参数、虚拟线程限流参数等），
         * 默认为 {@link HttpConfig} 的默认值
         */
        private HttpConfig httpConfig = HttpConfig.builder().build();
        /**
         * 一次性构建门：装配会消费注册参数并对全部中间件执行 init 与锁定，
         * 同一 Builder 只允许进入一次 {@link #build()}；重复调用在装配开始前抛出
         * {@link IllegalStateException}，第二次调用不触达任何组件
         */
        private final AtomicBoolean built = new AtomicBoolean(false);

        /**
         * 设置异常处理器管理器（按异常类型匹配处理器产出处理结果），传null立即抛出
         * {@link NullPointerException}
         */
        public Builder exceptionHandlerManager(ExceptionHandlerManager exceptionHandlerManager) {
            Objects.requireNonNull(exceptionHandlerManager, "The exception handler manager must not be null");
            this.exceptionHandlerManager = exceptionHandlerManager;
            return this;
        }

        /**
         * 设置路由处理器管理器，装配时包装成路由调用中间件追加在中间件链的末端，
         * 传null表示无路由（不创建该中间件）
         */
        public Builder routeHandlerManager(RouteHandlerManager routeHandlerManager) {
            this.routeHandlerManager = routeHandlerManager;
            return this;
        }

        /**
         * 替换session管理器，装配时与session参数打包为会话管理器持有者
         */
        public Builder sessionManager(SessionManager sessionManager) {
            Objects.requireNonNull(sessionManager, "The session manager must not be null");
            this.sessionManager = sessionManager;
            return this;
        }

        /**
         * 替换json序列化器；未显式设置结果转化器管理器时，用它补全默认结果转化器管理器
         */
        public Builder jsonSerializer(JsonSerializer jsonSerializer) {
            Objects.requireNonNull(jsonSerializer, "The json serializer must not be null");
            this.jsonSerializer = jsonSerializer;
            return this;
        }

        /**
         * 按顺序注册业务中间件；同一个中间件类只能注册一次，重复注册在build时抛出
         * {@link IllegalStateException}
         */
        public Builder middleware(Middleware... middlewares) {
            this.middlewares.addAll(
                    Stream.of(middlewares)
                            .peek(middleware -> Objects.requireNonNull(middleware, "The middleware must not be null"))
                            .toList()
            );
            return this;
        }

        /**
         * 设置跨域配置（编排器按其对响应写出CORS头），传null立即抛出 {@link NullPointerException}
         */
        public Builder corsConfig(HttpCorsConfig corsConfig) {
            Objects.requireNonNull(corsConfig, "The cors config must not be null");
            this.corsConfig = corsConfig;
            return this;
        }

        /**
         * 替换结果转化器管理器（业务返回值到http响应的转化链），传null立即抛出
         * {@link NullPointerException}
         */
        public Builder resultConverterManager(ResultConverterManager resultConverterManager) {
            Objects.requireNonNull(resultConverterManager, "The result converter manager must not be null");
            this.resultConverterManager = resultConverterManager;
            return this;
        }

        /**
         * 替换响应写出器管理器（按响应声明的写出器类型路由写出），传null立即抛出
         * {@link NullPointerException}
         */
        public Builder responseWriterManager(ResponseWriterManager responseWriterManager) {
            Objects.requireNonNull(responseWriterManager, "The response writer manager must not be null");
            this.responseWriterManager = responseWriterManager;
            return this;
        }

        /**
         * 整体替换应用纯参数配置（串行化开关、session回收与虚拟线程限流参数等），不能为null
         */
        public Builder config(HttpConfig config) {
            Objects.requireNonNull(config, "The HttpConfig must not be null");
            this.httpConfig = config;
            return this;
        }

        /**
         * 构建应用：串联并初始化中间件链，按 {@link HttpConfig} 的 diskOpeThread* 参数创建
         * 磁盘IO线程池，按限流参数创建虚拟线程资源，未传入的组件补全默认值后按九组件装配
         * 请求编排器（缺省字符集取自 {@link HttpConfig#getDefaultCharset()}），最后装配http请求调度处理器；
         * 连接级请求串行化开关同样取自 {@link HttpConfig#isSerializePerConnection()}。
         * 装配出的中间件链销毁适配器先登记、创建的磁盘IO线程池后登记进资源作用域交给应用持有，
         * 由 {@link Application#close()} 的默认实现按登记顺序释放。
         * <p>
         * 同一 Builder 仅允许构建一次，重复调用在装配开始前抛出 {@link IllegalStateException}
         *
         * @throws IllegalStateException 同一 Builder 重复调用 build()，或同一中间件类重复注册
         */
        public HttpRequestApplication build() {
            if (!built.compareAndSet(false, true)) {
                throw new IllegalStateException("build() can only be called once on the same Builder");
            }
            log.info("Assembling http request application...");
            // 应用持有的资源作用域：装配期创建的可关闭资源逐个登记进来，关闭时按登记顺序统一释放
            ScopedResource scopedResource = new ScopedResource();
            // 串联并初始化中间件链：链头为哨兵，存在路由处理器管理器时路由调用中间件追加链尾
            Middleware middlewareChain = initMiddlewareChain();
            // 中间件链销毁适配器先登记，保证应用关闭时中间件的destroy先于线程池的关闭执行
            scopedResource.register(() -> destroyMiddlewareChain(middlewareChain));
            // 创建磁盘IO线程池，默认响应写出器管理器注入它供文件流写出器调度分块读取；应用只是使用者，关闭责任留在应用
            ThreadPool diskOpeThreadPool = new ElasticThreadPool(
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum()
            );
            log.info("Disk IO thread pool created: cache queue {}, core queue {}, max threads {}",
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum());
            scopedResource.register(diskOpeThreadPool);
            // 按配置限流参数创建虚拟线程资源
            VirtualThreadResource virtualThreadResource = createVirtualThreadResource();
            // 未传入的组件补全默认值后，按九组件装定请求编排器
            RequestOrchestrator orchestrator = RequestOrchestrator.builder()
                    .middleware(middlewareChain)
                    .virtualThreadResource(virtualThreadResource)
                    .responseWriterManager(this.responseWriterManager != null
                            ? this.responseWriterManager
                            : new ResponseWriterManager(diskOpeThreadPool, List.of()))
                    .resultConverterManager(this.resultConverterManager != null
                            ? this.resultConverterManager
                            : new ResultConverterManager(this.jsonSerializer))
                    .corsConfig(this.corsConfig != null
                            ? this.corsConfig
                            : HttpCorsConfig.builder().build())
                    .exceptionHandlerManager(this.exceptionHandlerManager != null
                            ? this.exceptionHandlerManager
                            : new ExceptionHandlerManager(Set.of()))
                    .sessionManagerHolder(createSessionManagerHolder())
                    .jsonSerializer(this.jsonSerializer)
                    .defaultCharset(httpConfig.getDefaultCharset())
                    .build();
            HttpRequestSchedulerChannelHandler schedulerHandler = HttpRequestSchedulerChannelHandler.builder()
                    .orchestrator(orchestrator)
                    .build();
            log.info("Http request application assembled, per-connection request serialization: {}", httpConfig.isSerializePerConnection());
            return new HttpRequestApplication(
                    httpConfig.isSerializePerConnection(),
                    schedulerHandler,
                    scopedResource
            );
        }

        /**
         * 装配中间件链：以哨兵中间件为链头按注册顺序串联业务中间件（同一中间件类重复注册抛
         * {@link IllegalStateException}），存在路由处理器管理器时创建路由调用中间件追加在链尾，
         * 不存在则不创建；随后按链序依次执行各中间件的init与锁定
         *
         * @return 中间件链链头（哨兵中间件）
         */
        private Middleware initMiddlewareChain() {
            final Middleware sentinel = new SentinelMiddleware();
            Middleware tail = sentinel;
            // 用Set集合，防止middleware重复
            Set<Class<? extends Middleware>> registeredMiddlewares = new HashSet<>();
            for (Middleware middleware : middlewares) {
                if (registeredMiddlewares.contains(middleware.getClass())) {
                    throw new IllegalStateException("Duplicate middleware registration: " + middleware.getClass());
                }
                tail.setNext(middleware);
                tail = middleware;
                registeredMiddlewares.add(middleware.getClass());
            }
            // 存在路由处理器管理器时，包装成路由调用中间件添加到最后
            if (routeHandlerManager != null) {
                tail.setNext(new RouteHandlerInvokeMiddleware(routeHandlerManager));
            }
            // 执行所有中间件的初始化操作
            Middleware next = sentinel;
            while (next != null) {
                next.init(sentinel);
                next = next.getNext();
            }
            // 锁定所有的中间件
            next = sentinel;
            while (next != null) {
                next.lockMiddleware();
                next = next.getNext();
            }
            log.info("Middleware chain assembled: {} business middleware(s){}, all initialized and locked",
                    middlewares.size(), routeHandlerManager != null ? ", with route handler invoke middleware appended at the tail" : ", without a route handler manager");
            return sentinel;
        }

        /**
         * 按 {@link HttpConfig} 的限流参数创建虚拟线程资源：启用限流时以限流数量、最大挂起数、
         * 最大挂起时间构造 {@link LimitedVirtualThreadResource}（非公平抢占），
         * 未启用时构造 {@link UnlimitedVirtualThreadResource} 直通执行
         *
         * @return 虚拟线程资源
         */
        private VirtualThreadResource createVirtualThreadResource() {
            if (httpConfig.isEnableHttpSchedulerLimit()) {
                log.info("Virtual thread limit enabled: limit count {}, max suspended threads {}, suspend timeout {} ms",
                        httpConfig.getHttpSchedulerLimitCount(),
                        httpConfig.getHttpSchedulerLimitCacheThread(),
                        httpConfig.getHttpSchedulerLimitTimeout());
                return new LimitedVirtualThreadResource(
                        httpConfig.getHttpSchedulerLimitCount(),
                        httpConfig.getHttpSchedulerLimitCacheThread(),
                        httpConfig.getHttpSchedulerLimitTimeout(),
                        VIRTUAL_THREAD_NAME,
                        false);
            }
            log.info("Virtual thread limit disabled, request tasks execute directly in newly created virtual threads");
            return new UnlimitedVirtualThreadResource(VIRTUAL_THREAD_NAME);
        }

        /**
         * 创建session管理器持有者：以注册的session管理器与 {@link HttpConfig} 的session参数打包
         *
         * @return 会话管理器持有者
         */
        private SessionManagerHolder createSessionManagerHolder() {
            SessionManagerHolder sessionManagerHolder = new DefaultSessionManagerHolder(
                    this.sessionManager,
                    httpConfig.getSessionCheckTime(),
                    httpConfig.getSessionMaxNotUseTime(),
                    httpConfig.getSessionCheckThreshold());
            log.info("Session manager assembled: {}, check interval {} ms, max inactive time {} ms, check threshold {}",
                    this.sessionManager.getClass().getSimpleName(),
                    httpConfig.getSessionCheckTime(),
                    httpConfig.getSessionMaxNotUseTime(),
                    httpConfig.getSessionCheckThreshold());
            return sessionManagerHolder;
        }

    }

    /**
     * 路由处理器调用中间件：装配期追加在中间件链的末端，把当前请求上下文交给
     * {@link RouteHandlerManager} 处理，其返回结果作为链结果交后续结果转化与写出。
     */
    private static final class RouteHandlerInvokeMiddleware extends Middleware {

        private final RouteHandlerManager routeHandlerManager;

        private RouteHandlerInvokeMiddleware(RouteHandlerManager routeHandlerManager) {
            this.routeHandlerManager = routeHandlerManager;
        }

        @Override
        public Object invoke(HttpContext ctx) {
            return routeHandlerManager.handle(ctx);
        }
    }
}
