package top.turboweb.http.server.runtime;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.concurrent.thread.ElasticMicroTaskExecutor;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.concurrent.thread.VirtualThreadMicroTaskExecutor;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.server.runtime.ResourceManager;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.session.DefaultSessionManagerHolder;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.orchestrator.ExceptionHandlerManager;
import top.turboweb.http.orchestrator.HttpContentParser;
import top.turboweb.http.orchestrator.HttpContentParserManager;
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.orchestrator.RequestOrchestrator;
import top.turboweb.http.orchestrator.ResponseWriter;
import top.turboweb.http.orchestrator.ResponseWriterManager;
import top.turboweb.http.orchestrator.ResultConverter;
import top.turboweb.http.orchestrator.ResultConverterManager;
import top.turboweb.http.router.RouteHandlerManager;
import top.turboweb.http.router.RouteRegister;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.server.handler.HttpRequestSchedulerChannelHandler;
import top.turboweb.http.server.handler.RequestSerializerChannelHandler;
import top.turboweb.server.runtime.ServerRuntime;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * http请求应用，服务器与业务装配逻辑之间的桥梁。
 * <p>
 * 对外只暴露 {@link #configure(ChannelPipeline)}：每条新连接建立时向pipeline尾部追加
 * 请求串行化处理器（可选，连接级新实例）与http请求调度处理器（无状态单例，跨连接复用）；
 * 对内通过 {@link Builder} 在构建期一次性完成请求编排器的装配：以应用自身的 json 序列化器生产
 * 路由处理器管理器、串联中间件链（业务中间件按注册顺序，在其后追加路由调用中间件为链尾）、
 * 按 {@link HttpConfig} 创建磁盘IO线程池与虚拟线程资源、未由调用方传入的组件补全默认实例，
 * 按九组件装定 {@link RequestOrchestrator} 后接入调度处理器。
 * <p>
 * 本实例装配完成后不再持有可变状态，可被多个worker线程并发调用。
 * <p>
 * 装配期创建的生命周期资源不在本类中逐个持有，而是统一登记进本应用持有的
 * {@link ResourceManager} 资源管理器：中间件链的销毁适配器（沿链头到链尾调用各中间件的destroy）
 * 先登记，磁盘IO线程池、微任务执行器随后登记，关闭时由本类 {@link #close()} 按登记顺序
 * 统一释放。服务器关闭时会统一触发，调用方也可对应用实例自行try-with-resources。
 *
 * <pre>{@code
 * HttpServerRuntime runtime = new HttpServerRuntime.Builder()
 *         .routeRegister(routeRegister)
 *         .config(HttpConfig.builder()
 *                 .serializePerConnection(true)
 *                 .sessionMaxNotUseTime(1800000)
 *                 .build())
 *         .build();
 * }</pre>
 */
public class HttpServerRuntime implements ServerRuntime {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(HttpServerRuntime.class);

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
     * 应用的资源管理器：装配期由 {@link Builder#build()} 创建的磁盘IO线程池、微任务执行器、
     * 中间件链销毁适配器等可关闭资源全部登记于此，关闭时由 {@link #close()} 统一释放，
     * 使用方按 {@link ResourceNames} 的资源名从其中获取资源
     */
    private final ResourceManager resourceManager;

    public HttpServerRuntime(boolean requestSerial,
                                  HttpRequestSchedulerChannelHandler httpRequestSchedulerHandler,
                                  ResourceManager resourceManager) {
        this.requestSerial = requestSerial;
        this.httpRequestSchedulerHandler = httpRequestSchedulerHandler;
        this.resourceManager = Objects.requireNonNull(resourceManager, "The resource manager must not be null");
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
        log.debug("Http runtime handlers assembled for connection [{}], request serialization: {}",
                pipeline.channel().id().asShortText(), this.requestSerial);
    }

    /**
     * 获取本应用持有的资源管理器：装配期登记的可关闭资源在此，
     * 使用方按 {@link ResourceNames} 的资源名获取，关闭时由 {@link #close()} 统一释放
     */
    public ResourceManager getResourceManager() {
        return this.resourceManager;
    }

    /**
     * 关闭应用：释放 {@link #getResourceManager()} 中登记的全部资源，按登记顺序逐个释放，
     * 重复调用为空操作；单个资源释放失败只记录日志，不中断其余释放
     */
    @Override
    public void close() {
        this.resourceManager.free();
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
     * {@link HttpServerRuntime} 的建造者：持有应用装配所需的全部组件对象
     * （业务中间件、路由登记表、异常处理器集合、session管理器、json序列化器、
     * cors配置、自定义结果转化器集合、自定义响应写出器集合、自定义内容解析器集合），纯参数配置（串行化开关、session回收参数、
     * 虚拟线程限流参数）全部由 {@link HttpConfig} 承载；组件均带默认值，按需覆盖即可，
     * 未传入者构建时补全默认实例，装配完成的编排器组件均不可为null。
     * <p>
     * {@link #build()} 按以下顺序完成装配：
     * 以本建造者的 json 序列化器生产路由处理器管理器 -> 串联并初始化中间件链 ->
     * 按 {@link HttpConfig} 的 diskOpeThread* 参数创建磁盘IO线程池 ->
     * 按限流参数创建虚拟线程资源（启用限流走 {@link VirtualThreadMicroTaskExecutor#ofLimited}，
     * 否则走 {@link VirtualThreadMicroTaskExecutor#ofUnlimited}）-> 补全未传入的组件默认值 ->
     * 按九组件装配 {@link RequestOrchestrator} -> 装配http请求调度处理器。
     * 装配出的中间件链销毁适配器先登记、磁盘IO线程池与微任务执行器随后登记进应用的
     * {@link ResourceManager} 资源管理器，编排器与写出器管理器按 {@link ResourceNames}
     * 的资源名从管理器获取所需资源，由应用在关闭时按登记顺序统一释放。
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
         * 异常处理器实例集合，按注册顺序收集，构建期以登记后的异常类型表
         * 装配成 {@link ExceptionHandlerManager}；默认为空集合
         */
        private final List<Object> exceptionHandlers = new ArrayList<>();
        /**
         * 路由登记表，传null表示无路由：装配时不生产管理器、也不创建路由调用中间件；
         * 非null时于构建期收到本建造者的 json 序列化器并生产管理器
         */
        private RouteRegister routeRegister = null;
        /**
         * session管理器，默认内存实现 {@link MemorySessionManager}，装配时与session参数打包为会话管理器持有者
         */
        private SessionManager sessionManager = new MemorySessionManager();
        /**
         * json序列化器，默认Jackson实现，用于响应体序列化与session数据转换；
         * 构建期以它装配结果转化器管理器，并交给路由登记表生产路由处理器管理器，
         * 使序列化侧与路由侧持有同一实例
         */
        private JsonSerializer jsonSerializer = new JacksonJsonSerializer();
        /**
         * cors配置，未传入时默认 {@link HttpCorsConfig} 的通配配置
         */
        private HttpCorsConfig corsConfig = null;
        /**
         * 自定义结果转化器集合，按注册顺序登记为自定义环节，构建期与json序列化器一起
         * 装配成 {@link ResultConverterManager}；默认为空集合
         */
        private final List<ResultConverter> resultConverters = new ArrayList<>();
        /**
         * 自定义响应写出器集合，按注册顺序登记为自定义表，构建期与磁盘IO线程池一起
         * 装配成 {@link ResponseWriterManager}；默认为空集合
         */
        private final List<ResponseWriter> responseWriters = new ArrayList<>();
        /**
         * 自定义内容解析器集合，构建期与缺省字符集一起装配成 {@link HttpContentParserManager}；
         * 默认为空集合，此时只有内置的表单与JSON解析器生效
         */
        private final List<HttpContentParser> contentParsers = new ArrayList<>();
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
         * 按顺序登记异常处理器实例（异常统一处理链路的处理者，构建期装配成异常处理器
         * 管理器）；元素为null立即抛出 {@link NullPointerException}；处理方法按公有单异常
         * 参数签名认领，同一异常类型重复登记在构建时抛出
         */
        public Builder exceptionHandlers(Object... exceptionHandlers) {
            this.exceptionHandlers.addAll(
                    Stream.of(exceptionHandlers)
                            .peek(handler -> Objects.requireNonNull(handler, "The exception handler must not be null"))
                            .toList()
            );
            return this;
        }

        /**
         * 设置路由登记表：构建期以本建造者的 json 序列化器生产路由处理器管理器，
         * 装配时包装成路由调用中间件追加在中间件链的末端，传null表示无路由（不创建该中间件）
         */
        public Builder routeRegister(RouteRegister routeRegister) {
            this.routeRegister = routeRegister;
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
         * 替换json序列化器；构建期以它装配结果转化器管理器，并交给路由登记表生产路由处理器管理器，
         * 因此入口的调用顺序不影响路由侧拿到的实例（生产发生在构建期）
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
         * 按顺序注册自定义结果转化器（业务返回值到http响应的转化链的自定义环节，
         * 构建期与json序列化器装配成结果转化器管理器）；元素为null立即抛出
         * {@link NullPointerException}；内置类型拒登、同一转化器类重复注册在构建时抛出
         */
        public Builder resultConverters(ResultConverter... resultConverters) {
            this.resultConverters.addAll(
                    Stream.of(resultConverters)
                            .peek(converter -> Objects.requireNonNull(converter, "The result converter must not be null"))
                            .toList()
            );
            return this;
        }

        /**
         * 按顺序注册自定义响应写出器（http响应写出的自定义表环节，构建期与磁盘IO线程池
         * 装配成响应写出器管理器）；元素为null立即抛出 {@link NullPointerException}；
         * 内置类型拒登、同一写出器类重复注册在构建时抛出
         */
        public Builder responseWriters(ResponseWriter... responseWriters) {
            this.responseWriters.addAll(
                    Stream.of(responseWriters)
                            .peek(writer -> Objects.requireNonNull(writer, "The response writer must not be null"))
                            .toList()
            );
            return this;
        }

        /**
         * 按顺序注册自定义内容解析器（构建期与缺省字符集一起装配成内容解析器管理器，
         * 按请求头声明的Content-Type路由）；元素为null立即抛出 {@link NullPointerException}；
         * 声明的类型与内置解析器冲突、或多个解析器声明同一类型在构建时抛出
         * {@link IllegalArgumentException}
         */
        public Builder contentParsers(HttpContentParser... contentParsers) {
            this.contentParsers.addAll(
                    Stream.of(contentParsers)
                            .peek(parser -> Objects.requireNonNull(parser, "The content parser must not be null"))
                            .toList()
            );
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
         * 构建应用：先以本建造者的 json 序列化器生产路由处理器管理器（未挂登记表则无路由），
         * 再串联并初始化中间件链，按 {@link HttpConfig} 的 diskOpeThread* 参数创建
         * 磁盘IO线程池，按限流参数创建虚拟线程资源，未传入的组件补全默认值后按九组件装配
         * 请求编排器（缺省字符集取自 {@link HttpConfig#getDefaultCharset()}，内容解析器管理器
         * 以同一字符集与本建造者登记的自定义解析器建立），最后装配http请求调度处理器；
         * 连接级请求串行化开关同样取自 {@link HttpConfig#isSerializePerConnection()}。
         * 装配出的中间件链销毁适配器经托管通道登记、创建的磁盘IO微任务执行器与请求微任务执行器
         * 作为内置资源随构建传入资源管理器交给应用持有，编排器与响应写出器管理器按资源名
         * 从管理器的内置空间获取所需资源，由 {@link #close()} 按登记顺序释放。
         * <p>
         * 同一 Builder 仅允许构建一次，重复调用在装配开始前抛出 {@link IllegalStateException}
         *
         * @throws IllegalStateException 同一 Builder 重复调用 build()，或同一中间件类重复注册
         */
        public HttpServerRuntime build() {
            if (!built.compareAndSet(false, true)) {
                throw new IllegalStateException("build() can only be called once on the same Builder");
            }
            log.info("Assembling http request runtime...");
            // 应用持有的资源管理器：装配期创建的可关闭资源经托管通道逐个登记进来，
            // 内置资源在构建时一次性传入，关闭时按登记顺序统一释放，
            // 使用方按ResourceNames的资源名从内置空间获取
            ResourceManager.Builder resourceBuilder = new ResourceManager.Builder();
            // 路由生产：以本建造者的json序列化器装配登记表，使路由侧与结果转化侧共用同一序列化实例
            RouteHandlerManager routeHandlerManager = this.routeRegister != null
                    ? this.routeRegister.newRouteHandlerManager(this.jsonSerializer)
                    : null;
            // 串联并初始化中间件链：链头为哨兵，存在路由处理器管理器时路由调用中间件追加链尾
            Middleware middlewareChain = initMiddlewareChain(routeHandlerManager);
            // 中间件链销毁适配器以托管资源登记，保证应用关闭时中间件的destroy先于线程池的关闭执行
            resourceBuilder.addManaged(() -> destroyMiddlewareChain(middlewareChain));
            // 创建磁盘IO微任务执行器，构建期注入响应写出器管理器供文件流写出器调度分块读取；应用只是使用者，关闭责任留在应用
            MicroTaskExecutor diskIoExecutor = ElasticMicroTaskExecutor.of(
                    "disk-io-micro-task",
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum()
            );
            log.info("Disk IO micro task executor created: cache queue {}, core queue {}, max threads {}",
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum());
            // 磁盘IO微任务执行器与请求微任务执行器作为内置资源随构建一次性传入，
            // 传入顺序即释放顺序，保持磁盘IO执行器在前、请求微任务执行器在后
            Map<String, AutoCloseable> builtinResources = new LinkedHashMap<>();
            builtinResources.put(ResourceNames.DISK_IO_THREAD_POOL, diskIoExecutor);
            // 按配置限流参数创建虚拟线程资源
            VirtualThreadMicroTaskExecutor virtualThreadResource = createVirtualThreadResource();
            builtinResources.put(ResourceNames.MICRO_TASK_EXECUTOR, virtualThreadResource);
            ResourceManager resourceManager = resourceBuilder.build(builtinResources);
            // 未传入的组件补全默认值后，按九组件装定请求编排器；微任务执行器与磁盘IO执行器按资源名从管理器获取
            RequestOrchestrator orchestrator = RequestOrchestrator.builder()
                    .middleware(middlewareChain)
                    .resourceManager(resourceManager)
                    .responseWriterManager(new ResponseWriterManager(resourceManager, this.responseWriters))
                    .resultConverterManager(new ResultConverterManager(this.jsonSerializer, this.resultConverters))
                    .corsConfig(this.corsConfig != null
                            ? this.corsConfig
                            : HttpCorsConfig.builder().build())
                    .exceptionHandlerManager(new ExceptionHandlerManager(Set.copyOf(this.exceptionHandlers)))
                    .sessionManagerHolder(createSessionManagerHolder())
                    .defaultCharset(httpConfig.getDefaultCharset())
                    .contentParserManager(new HttpContentParserManager(httpConfig.getDefaultCharset(), this.contentParsers))
                    .build();
            HttpRequestSchedulerChannelHandler schedulerHandler = HttpRequestSchedulerChannelHandler.builder()
                    .orchestrator(orchestrator)
                    .build();
            log.info("Http request runtime assembled, per-connection request serialization: {}", httpConfig.isSerializePerConnection());
            return new HttpServerRuntime(
                    httpConfig.isSerializePerConnection(),
                    schedulerHandler,
                    resourceManager
            );
        }

        /**
         * 装配中间件链：以哨兵中间件为链头按注册顺序串联业务中间件（同一中间件类重复注册抛
         * {@link IllegalStateException}），存在路由处理器管理器时创建路由调用中间件追加在链尾，
         * 不存在则不创建；随后按链序依次执行各中间件的init与锁定
         *
         * @param routeHandlerManager 构建期由路由登记表生产出的路由处理器管理器，为null时链尾不挂路由节点
         * @return 中间件链链头（哨兵中间件）
         */
        private Middleware initMiddlewareChain(RouteHandlerManager routeHandlerManager) {
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
         * 最大挂起时间走 {@link VirtualThreadMicroTaskExecutor#ofLimited}（非公平抢占），
         * 未启用时走 {@link VirtualThreadMicroTaskExecutor#ofUnlimited} 直通执行
         *
         * @return 虚拟线程资源
         */
        private VirtualThreadMicroTaskExecutor createVirtualThreadResource() {
            if (httpConfig.isEnableHttpSchedulerLimit()) {
                log.info("Virtual thread limit enabled: limit count {}, max suspended threads {}, suspend timeout {} ms",
                        httpConfig.getHttpSchedulerLimitCount(),
                        httpConfig.getHttpSchedulerLimitCacheThread(),
                        httpConfig.getHttpSchedulerLimitTimeout());
                return VirtualThreadMicroTaskExecutor.ofLimited(
                        httpConfig.getHttpSchedulerLimitCount(),
                        httpConfig.getHttpSchedulerLimitCacheThread(),
                        httpConfig.getHttpSchedulerLimitTimeout(),
                        VIRTUAL_THREAD_NAME,
                        false);
            }
            log.info("Virtual thread limit disabled, request tasks execute directly in newly created virtual threads");
            return VirtualThreadMicroTaskExecutor.ofUnlimited(VIRTUAL_THREAD_NAME);
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
