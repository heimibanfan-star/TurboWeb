package top.turboweb.http.server.application;

import io.netty.channel.ChannelPipeline;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.serializer.JsonSerializer;
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
import top.turboweb.http.server.handler.HttpRequestSchedulerHandler;
import top.turboweb.http.server.handler.RequestSerializerHandler;
import top.turboweb.http.session.DefaultSessionManagerHolder;
import top.turboweb.http.session.MemorySessionManager;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
     * 开启后每条连接的pipeline中先加入 {@link RequestSerializerHandler}，
     * 保证同一连接的请求按到达顺序被处理
     */
    private final boolean requestSerial;

    /**
     * http请求调度处理器，构建期装配好的无状态单例（{@code @Sharable}），
     * 负责把请求转交给内部http调度器执行处理器责任链
     */
    private final HttpRequestSchedulerHandler httpRequestSchedulerHandler;

    public HttpRequestApplication(boolean requestSerial, HttpRequestSchedulerHandler httpRequestSchedulerHandler) {
        this.requestSerial = requestSerial;
        this.httpRequestSchedulerHandler = httpRequestSchedulerHandler;
    }


    /**
     * 每条新连接建立时被调用一次，向pipeline尾部追加应用级处理器。
     * 串行化处理器有连接级状态必须每次新建，调度处理器为单例直接复用
     */
    @Override
    public void configure(ChannelPipeline pipeline) {
        if (this.requestSerial) {
            pipeline.addLast(new RequestSerializerHandler());
        }
        pipeline.addLast(httpRequestSchedulerHandler);
        log.debug("连接[{}]装配http应用处理器完成，请求串行化: {}",
                pipeline.channel().id().asShortText(), this.requestSerial);
    }

    /**
     * {@link HttpRequestApplication} 的建造者：持有应用装配所需的全部组件对象
     * （中间件链、异常处理器、session管理器、json序列化器、路由管理器、cors配置、响应转换器），
     * 纯参数配置（串行化开关、请求日志开关、session回收参数、调度器限流参数）全部由
     * {@link HttpConfig} 承载；组件均带默认值，按需覆盖即可。
     * <p>
     * {@link #build()} 按以下顺序完成装配：
     * 补全响应转换器 -> 串联三大处理器（CORS -> 异常处理 -> 中间件执行）->
     * 初始化http调度器（应用限流与请求日志配置）-> 构建http请求调度处理器。
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
         * 构建应用：补全默认响应转换器，串联处理器责任链，按 {@link HttpConfig}
         * 初始化虚拟线程调度器（限流参数与请求日志开关），最后装配http请求调度处理器；
         * 连接级请求串行化开关同样取自 {@link HttpConfig#isSerializePerConnection()}
         */
        public HttpRequestApplication build() {
            log.info("开始装配http请求应用...");
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
            // 初始化http调度器
            HttpScheduler httpScheduler = new VirtualThreadHttpScheduler(
                    corsProcessor,
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
            HttpRequestSchedulerHandler schedulerHandler = HttpRequestSchedulerHandler.builder()
                    .httpScheduler(httpScheduler)
                    .build();
            log.info("http请求应用装配完成，连接级请求串行化: {}, 请求日志: {}",
                    httpConfig.isSerializePerConnection(), httpConfig.isShowRequestLog());
            return new HttpRequestApplication(httpConfig.isSerializePerConnection(), schedulerHandler);
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
