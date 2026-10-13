package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.commons.utils.resource.ScopedResource;
import top.turboweb.concurrent.thread.ElasticThreadPool;
import top.turboweb.concurrent.thread.ThreadPool;
import top.turboweb.http.handler.DefaultExceptionHandlerMatcher;
import top.turboweb.http.handler.ExceptionHandlerContainer;
import top.turboweb.http.handler.ExceptionHandlerContainerInitHelper;
import top.turboweb.http.handler.ExceptionHandlerMatcher;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.SentinelMiddleware;
import top.turboweb.http.middleware.router.RouterManager;
import top.turboweb.http.processor.CorsProcessor;
import top.turboweb.http.processor.ExceptionHandlerProcessor;
import top.turboweb.http.processor.MiddlewareInvokeProcessor;
import top.turboweb.http.processor.convertor.DefaultHttpResponseConverter;
import top.turboweb.http.processor.convertor.HttpResponseConverter;
import top.turboweb.http.scheduler.HttpScheduler;
import top.turboweb.http.scheduler.VirtualThreadHttpScheduler;
import top.turboweb.http.server.config.HttpConfig;
import top.turboweb.http.server.handler.HttpRequestSchedulerChannelHandler;
import top.turboweb.http.server.handler.RequestSerializerChannelHandler;
import top.turboweb.http.core.session.DefaultSessionManagerHolder;
import top.turboweb.http.core.session.MemorySessionManager;
import top.turboweb.http.core.session.SessionManager;
import top.turboweb.http.core.session.SessionManagerHolder;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * http请求应用，服务器与业务装配逻辑之间的桥梁。
 * <p>
 * 对外只暴露 {@link #configure(ChannelPipeline)}：每条新连接建立时向pipeline尾部追加
 * 请求串行化处理器（可选，连接级新实例）与http请求调度处理器（无状态单例，跨连接复用）；
 * 对内通过 {@link Builder} 在构建期一次性完成处理器责任链
 * （CORS -> 异常处理 -> 中间件执行）、虚拟线程http调度器与调度处理器的装配。
 * <p>
 * 本实例装配完成后不再持有可变状态，可被多个worker线程并发调用。
 * <p>
 * 装配期创建的生命周期资源不在本类中逐个持有，而是统一登记进本应用持有的
 * {@link ScopedResource} 资源作用域：中间件链的销毁适配器（沿链头到链尾调用各中间件的destroy）
 * 先登记，磁盘IO线程池后登记，关闭时由 {@link Application#close()} 按登记顺序释放整个作用域。
 * 服务器关闭时会统一触发，调用方也可对应用实例自行try-with-resources。
 *
 * <pre>{@code
 * HttpRequestApplication application = new HttpRequestApplication.Builder()
 *         .routerManager(routerManager)
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
     * 是否开启连接级请求串行化（来源为 {@link HttpConfig#isSerializePerConnection()}），
     * 开启后每条连接的pipeline中先加入 {@link RequestSerializerChannelHandler}，
     * 保证同一连接的请求按到达顺序被处理
     */
    private final boolean requestSerial;

    /**
     * http请求调度处理器，构建期装配好的无状态单例（{@code @Sharable}），
     * 负责把请求转交给内部http调度器执行处理器责任链
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
        this.scopedResource = Objects.requireNonNull(scopedResource, "资源作用域不能为null");
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
        log.debug("连接[{}]装配http应用处理器完成，请求串行化: {}",
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
     * 包含追加在链尾的路由管理器，再逐个调用 {@link Middleware#destroy()}，
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
                log.error("中间件销毁异常: {}", middleware.getClass().getName(), e);
            }
        }
        log.info("中间件链销毁完成，共 {} 个", chain.size());
    }

    /**
     * {@link HttpRequestApplication} 的建造者：持有应用装配所需的全部组件对象
     * （中间件链、异常处理器、session管理器、json序列化器、路由管理器、cors配置、响应转换器），
     * 纯参数配置（串行化开关、请求日志开关、session回收参数、调度器限流参数）全部由
     * {@link HttpConfig} 承载；组件均带默认值，按需覆盖即可。
     * <p>
     * {@link #build()} 按以下顺序完成装配：
     * 补全响应转换器 -> 串联三大处理器（CORS -> 异常处理 -> 中间件执行）->
     * 按 {@link HttpConfig} 创建磁盘IO线程池 -> 初始化http调度器（注入线程池与应用限流配置）->
     * 构建http请求调度处理器。装配出的中间件链销毁适配器与磁盘IO线程池依次登记进应用的
     * {@link ScopedResource} 资源作用域，由应用在关闭时按登记顺序统一释放。
     * <p>
     * Builder 为一次性装配载体：{@link #build()} 仅允许调用一次，重复调用在装配开始前抛出
     * {@link IllegalStateException}。
     */
    public static class Builder {

        /**
         * 业务中间件集合，按注册顺序串联成链，路由管理器在装配时追加在链尾
         */
        private final List<Middleware> middlewares = new ArrayList<>();
        /**
         * 异常处理器对象集合，构建时解析为异常处理容器
         */
        private final List<Object> exceptionHandlers = new ArrayList<>();
        /**
         * session管理器，默认内存实现 {@link MemorySessionManager}
         */
        private SessionManager sessionManager = new MemorySessionManager();
        /**
         * json序列化器，默认Jackson实现，用于响应体序列化与session数据转换
         */
        private JsonSerializer jsonSerializer = new JacksonJsonSerializer();
        /**
         * 路由管理器，可为null表示不挂路由
         */
        private RouterManager routerManager = null;
        /**
         * cors配置消费者，默认为空操作（使用CorsProcessor自带的默认配置）
         */
        private Consumer<CorsProcessor.Config> corsConfigConsumer = config -> {};
        /**
         * 响应转换器，为null时build()用jsonSerializer补全为 {@link DefaultHttpResponseConverter}
         */
        private HttpResponseConverter responseConverter = null;
        /**
         * 应用纯参数配置（串行化开关、请求日志开关、session回收参数、调度器限流参数等），
         * 默认为 {@link HttpConfig} 的默认值
         */
        private HttpConfig httpConfig = HttpConfig.builder().build();
        /**
         * 中间件链链头（哨兵中间件），由 {@link #initMiddlewareInvokeProcessor()} 装配时回填，
         * {@link #build()} 用它生成销毁适配器登记进应用的资源作用域
         */
        private Middleware middlewareChain = null;
        /**
         * 一次性构建门：装配会消费注册参数、回填链头并对全部中间件执行 init 与锁定，
         * 同一 Builder 只允许进入一次 {@link #build()}；重复调用在装配开始前抛出
         * {@link IllegalStateException}，第二次调用不触达任何组件
         */
        private final AtomicBoolean built = new AtomicBoolean(false);

        /**
         * 注册异常处理器（内部方法标注了异常处理注解的类实例），可多次调用叠加，
         * 参数为null时立即抛出 {@link NullPointerException}
         */
        public Builder exceptionHandler(Object... handlers) {
            exceptionHandlers.addAll(
                    Stream.of(handlers)
                            .peek(handler -> Objects.requireNonNull(handler, "异常处理器不能为空"))
                            .toList()
            );
            return this;
        }

        /**
         * 替换session管理器，决定中间件链尾部的会话存储与回收行为
         */
        public Builder sessionManager(SessionManager sessionManager) {
            Objects.requireNonNull(sessionManager, "session管理器不能为空");
            this.sessionManager = sessionManager;
            return this;
        }

        /**
         * 设置路由管理器，装配时追加在中间件链的末端，传null表示无路由
         */
        public Builder routerManager(RouterManager routerManager) {
            this.routerManager = routerManager;
            return this;
        }

        /**
         * 替换json序列化器；未显式设置响应转换器时，用它补全默认响应转换器
         */
        public Builder jsonSerializer(JsonSerializer jsonSerializer) {
            Objects.requireNonNull(jsonSerializer, "json序列化器不能为null");
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
                            .peek(middleware -> Objects.requireNonNull(middleware, "中间件不能为null"))
                            .toList()
            );
            return this;
        }

        /**
         * 配置跨域策略，build时通过消费者修改 {@link CorsProcessor.Config}
         */
        public Builder cors(Consumer<CorsProcessor.Config> config) {
            Objects.requireNonNull(config, "cors配置消费者不能为null");
            this.corsConfigConsumer = config;
            return this;
        }

        /**
         * 替换响应转换器（业务返回值到http响应的转换逻辑）
         */
        public Builder responseConverter(HttpResponseConverter responseConverter) {
            this.responseConverter = responseConverter;
            return this;
        }

        /**
         * 整体替换应用纯参数配置（串行化开关、请求日志开关、session回收与调度器限流参数等），不能为null
         */
        public Builder config(HttpConfig config) {
            Objects.requireNonNull(config, "HttpConfig不能为null");
            this.httpConfig = config;
            return this;
        }

        /**
         * 构建应用：补全默认响应转换器，串联处理器责任链，按 {@link HttpConfig} 的
         * diskOpeThread* 参数创建磁盘IO线程池，注入虚拟线程调度器（同时应用限流参数与请求日志开关），
         * 最后装配http请求调度处理器；
         * 连接级请求串行化开关同样取自 {@link HttpConfig#isSerializePerConnection()}。
         * 装配出的中间件链销毁适配器与创建的磁盘IO线程池依次登记进资源作用域交给应用持有，
         * 由 {@link Application#close()} 的默认实现按登记顺序释放。
         * <p>
         * 同一 Builder 仅允许构建一次，重复调用在装配开始前抛出 {@link IllegalStateException}
         *
         * @throws IllegalStateException 同一 Builder 重复调用 build()，或同一中间件类重复注册
         */
        public HttpRequestApplication build() {
            if (!built.compareAndSet(false, true)) {
                throw new IllegalStateException("Builder 已构建过应用，不能重复 build()");
            }
            log.info("开始装配http请求应用...");
            // 应用持有的资源作用域：装配期创建的可关闭资源逐个登记进来，关闭时按登记顺序统一释放
            ScopedResource scopedResource = new ScopedResource();
            if (this.responseConverter == null) {
                this.responseConverter = new DefaultHttpResponseConverter(this.jsonSerializer);
            }
            // 初始化三大处理器
            CorsProcessor corsProcessor = initCorsProcessor();
            ExceptionHandlerProcessor exceptionHandlerProcessor = initExceptionHandlerProcessor();
            MiddlewareInvokeProcessor middlewareInvokeProcessor = initMiddlewareInvokeProcessor();
            corsProcessor.setNextProcessor(exceptionHandlerProcessor);
            exceptionHandlerProcessor.setNextProcessor(middlewareInvokeProcessor);
            log.info("处理器责任链装配完成：CORS -> 异常处理 -> 中间件执行");
            // 中间件链销毁适配器先登记，保证应用关闭时中间件的destroy先于线程池的关闭执行
            Middleware middlewareChain = this.middlewareChain;
            scopedResource.register(() -> destroyMiddlewareChain(middlewareChain));
            // 创建磁盘IO线程池，供文件流响应策略调度分块读取；调度器只是使用者，关闭责任交给应用
            ThreadPool diskOpeThreadPool = new ElasticThreadPool(
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum()
            );
            log.info("磁盘IO线程池创建完成：缓冲队列 {}, 核心队列 {}, 最大线程数 {}",
                    httpConfig.getDiskOpeThreadCacheQueue(),
                    httpConfig.getDiskOpeThreadCoreQueue(),
                    httpConfig.getDiskOpeThreadMaxThreadNum());
            scopedResource.register(diskOpeThreadPool);
            // 初始化http调度器
            HttpScheduler httpScheduler = new VirtualThreadHttpScheduler(
                    corsProcessor,
                    diskOpeThreadPool,
                    httpConfig.isEnableHttpSchedulerLimit(),
                    httpConfig.getHttpSchedulerLimitCount(),
                    httpConfig.getHttpSchedulerLimitCacheThread(),
                    httpConfig.getHttpSchedulerLimitTimeout()
            );
            // 应用请求日志开关，控制调度器完成后是否输出方法/URI/耗时日志
            httpScheduler.setShowRequestLog(httpConfig.isShowRequestLog());
            if (httpConfig.isEnableHttpSchedulerLimit()) {
                log.info("http调度器限流已开启：限流数量 {}, 等待线程上限 {}, 超时 {} ms",
                        httpConfig.getHttpSchedulerLimitCount(),
                        httpConfig.getHttpSchedulerLimitCacheThread(),
                        httpConfig.getHttpSchedulerLimitTimeout());
            } else {
                log.info("http调度器限流未开启");
            }
            HttpRequestSchedulerChannelHandler schedulerHandler = HttpRequestSchedulerChannelHandler.builder()
                    .httpScheduler(httpScheduler)
                    .build();
            log.info("http请求应用装配完成，连接级请求串行化: {}, 请求日志: {}",
                    httpConfig.isSerializePerConnection(), httpConfig.isShowRequestLog());
            return new HttpRequestApplication(
                    httpConfig.isSerializePerConnection(),
                    schedulerHandler,
                    scopedResource
            );
        }

        /**
         * 初始化CORS处理器：创建处理器后立即交给配置消费者修改其配置
         */
        private CorsProcessor initCorsProcessor() {
            Consumer<CorsProcessor.Config> corsConfigConsumer = this.corsConfigConsumer;
            CorsProcessor corsProcessor = new CorsProcessor();
            CorsProcessor.Config config = corsProcessor.getConfig();
            corsConfigConsumer.accept(config);
            return corsProcessor;
        }

        /**
         * 初始化异常处理器：将注册的处理器对象解析为异常处理容器，并绑定响应转换器
         */
        private ExceptionHandlerProcessor initExceptionHandlerProcessor() {
            ExceptionHandlerContainer handlerContainer = ExceptionHandlerContainerInitHelper
                    .initContainer(this.exceptionHandlers);
            ExceptionHandlerMatcher matcher = new DefaultExceptionHandlerMatcher(handlerContainer);
            log.info("异常处理器初始化完成，共注册 {} 个", this.exceptionHandlers.size());
            return new ExceptionHandlerProcessor(matcher, this.responseConverter);
        }

        /**
         * 初始化中间件执行处理器：以哨兵中间件为链头按注册顺序串联业务中间件，
         * 路由管理器追加在链尾；随后依次执行各中间件的init与锁定，
         * 并用 {@link HttpConfig} 的session参数创建session管理器持有者
         */
        private MiddlewareInvokeProcessor initMiddlewareInvokeProcessor() {
            List<Middleware> middlewares = this.middlewares;
            RouterManager routerManager = this.routerManager;
            HttpConfig httpConfig = this.httpConfig;
            // 哨兵中间件，位于第一个占位
            final Middleware sentinel = new SentinelMiddleware();
            Middleware tail = sentinel;
            // 用Set集合，防止middleware重复
            Set<Class<? extends Middleware>> registedMiddlewares = new HashSet<>();
            for (Middleware middleware : middlewares) {
                if (registedMiddlewares.contains(middleware.getClass())) {
                    throw new IllegalStateException("中间件重复注册:" + middleware.getClass());
                }
                tail.setNext(middleware);
                tail = middleware;
                registedMiddlewares.add(middleware.getClass());
            }
            // 将路由管理器添加到最后
            if (routerManager != null) {
                tail.setNext(routerManager);
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
            // 回填链头，build() 用它生成销毁适配器登记进应用的资源作用域
            this.middlewareChain = sentinel;
            log.info("中间件链装配完成：业务中间件 {} 个{}，已全部初始化并锁定",
                    middlewares.size(), routerManager != null ? "，路由管理器已追加链尾" : "，无路由管理器");
            // 创建session管理器
            SessionManagerHolder sessionManagerHolder = new DefaultSessionManagerHolder(
                    this.sessionManager,
                    httpConfig.getSessionCheckTime(),
                    httpConfig.getSessionMaxNotUseTime(),
                    httpConfig.getSessionCheckThreshold()
            );
            log.info("session管理器装配完成：{}，检查间隔 {} ms，最大不活跃时间 {} ms，检查阈值 {}",
                    this.sessionManager.getClass().getSimpleName(),
                    httpConfig.getSessionCheckTime(),
                    httpConfig.getSessionMaxNotUseTime(),
                    httpConfig.getSessionCheckThreshold());
            return new MiddlewareInvokeProcessor(
                    sentinel,
                    sessionManagerHolder,
                    this.responseConverter,
                    this.jsonSerializer
            );
        }

    }
}
