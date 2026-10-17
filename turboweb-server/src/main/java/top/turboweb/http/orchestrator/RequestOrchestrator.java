package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.rest.Http;
import top.turboweb.concurrent.thread.VirtualThreadResource;
import top.turboweb.http.context.DefaultMessage;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpUnknowContent;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.cookie.DefaultHttpCookies;
import top.turboweb.rest.HttpCookies;
import top.turboweb.http.core.session.DefaultHttpSession;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.core.session.SessionManager;
import top.turboweb.http.core.session.SessionManagerHolder;
import top.turboweb.http.middleware.Middleware;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * 请求编排器：以建造者装配九个协作组件——中间件链、虚拟线程资源、
 * 响应写出器管理器、结果转化器管理器、cors 配置、异常处理器管理器、
 * 会话管理器持有者、缺省字符集与请求体内容解析器管理器
 * （{@link HttpContentParserManager}，由装配方按缺省字符集与自定义解析器建立后传入）。
 * <p>
 * 组件全部 final、构造后不可变更；非空契约两级拒收：
 * 建造者配置方法对 null 入参即时拒绝，{@link Builder#build()} 对缺配组件拒绝并指名。
 * 磁盘IO线程池已在写出器管理器构造期装配，本类不直接持有；
 * 虚拟线程资源、中间件链与会话管理器持有者仅持有、不负责其生命周期。
 * </p>
 * <p>
 * 已实现的流程：{@link #orchestrate} 把请求任务提交到虚拟线程资源，
 * 任务被拒（资源耗尽且不排队）时直接写出 HTTP 429 响应；
 * {@link #doOrchestrate} 在任务线程内执行完整编排——OPTIONS 预检不进中间件链、
 * 直接以空正文 200 携带 CORS 响应头应答；其余请求先把请求转化为上下文的消息、
 * 并按内容类型解析请求体得到内容对象，两者一并装配进请求上下文（上下文不接触请求对象）
 * 后执行中间件链并经结果转化链产出响应，过程中抛出的异常交由异常处理器管理器产出处理结果后转化，
 * 响应统一经 CORS 来源处理后写出，请求在写出提交后释放（含异常路径）；
 * 写出可监听时挂接完成回调以 DEBUG 级打印请求日志（方法、URI、耗时、写出成败），
 * 展示与否由日志级别控制，不可监听写出不记录。
 * {@link #handleCorsHeaders} 亦可独立调用。
 * </p>
 * <p><b>线程安全性：</b>实例设计为单例共享，可在任意线程调用。</p>
 */
public class RequestOrchestrator {

    /** 请求日志记录器，走 netty 标准日志通道，DEBUG 级输出。 */
    private static final InternalLogger log = InternalLoggerFactory.getInstance(RequestOrchestrator.class);
    /** 中间件链链头，请求处理流程的入口组件。 */
    private final Middleware middleware;
    /** 虚拟线程资源，承载请求任务的线程创建与限流。 */
    private final VirtualThreadResource virtualThreadResource;
    /** 响应写出器管理器，按响应声明的写出器类型路由写出。 */
    private final ResponseWriterManager responseWriterManager;
    /** 结果转化器管理器，把业务返回值转化为 HTTP 响应。 */
    private final ResultConverterManager resultConverterManager;
    /** CORS 不可变配置，来源处理流程的判定依据。 */
    private final HttpCorsConfig corsConfig;
    /** 异常处理器管理器，按异常类型匹配处理器产出处理结果。 */
    private final ExceptionHandlerManager exceptionHandlerManager;
    /** 会话管理器持有者，构建请求上下文时提供会话存储。 */
    private final SessionManagerHolder sessionManagerHolder;
    /** 缺省字符集（装配期由 HttpConfig 判定），随请求传入上下文，并用于响应转化与 Cookie 写出编码。 */
    private final Charset defaultCharset;
    /** 请求体内容解析器管理器：由建造者装配传入，装配上下文时按内容类型解析请求体。 */
    private final HttpContentParserManager contentParserManager;

    /**
     * 由建造者装配产物，编排器对外不提供公有构造入口；
     * 组件实例原样持有，不在本构造期建立或复制。
     *
     * @param builder 已收集完九组件的建造者
     */
    private RequestOrchestrator(Builder builder) {
        this.middleware = builder.middleware;
        this.virtualThreadResource = builder.virtualThreadResource;
        this.responseWriterManager = builder.responseWriterManager;
        this.resultConverterManager = builder.resultConverterManager;
        this.corsConfig = builder.corsConfig;
        this.exceptionHandlerManager = builder.exceptionHandlerManager;
        this.sessionManagerHolder = builder.sessionManagerHolder;
        this.defaultCharset = builder.defaultCharset;
        this.contentParserManager = builder.contentParserManager;
    }

    /**
     * 创建建造者，九组件均无默认值，须逐一配置齐备后构建。
     *
     * @return 新建造者
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 编排请求入口：取起始时刻后把请求提交到虚拟线程资源，获取资源后在任务线程内
     * 执行 {@link #doOrchestrate}；任务被拒时（资源耗尽且不允许排队或排队失败）
     * 不经中间件链与 CORS 处理，直接写出 HTTP 429 响应并释放请求。
     * 正常与拒绝两路写出均可监听时挂接完成回调打印 DEBUG 级请求日志，
     * 耗时自本入口起始时刻起算、含任务提交前的排队等待。
     *
     * @param request        完整的 HTTP 请求，不能为 null
     * @param connectSession 当前连接会话，不能为 null，响应经其通道写出
     * @throws NullPointerException 任一参数为 null
     */
    public void orchestrate(FullHttpRequest request, ConnectSession connectSession) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(connectSession, "connect session must not be null");
        long startTime = System.nanoTime();
        virtualThreadResource.run(
                () -> doOrchestrate(request, connectSession, startTime),
                rejectionThread -> {
                    try {
                        attachRequestLog(request, startTime,
                                responseWriterManager.write(toManyRequestsResponse(), connectSession));
                    } finally {
                        request.release();
                    }
                });
    }

    /**
     * 在当前线程执行完整编排流程并返回写出凭据：OPTIONS 请求以空正文 200
     * 携带 CORS 响应头直接应答、不进中间件链；其余请求执行中间件链并把结果
     * 经结果转化链转化为响应，链执行或转化过程中抛出的异常交由
     * {@link ExceptionHandlerManager#handle(Throwable)} 产出处理结果后再转化；
     * 正常与异常响应统一经 {@link #handleCorsHeaders} 写出 CORS 头后交
     * {@link ResponseWriterManager#write} 写出。写出可监听时挂接完成回调，
     * 以 DEBUG 级打印请求日志（方法、URI、自起点耗时、写出成败），展示与否由日志级别控制，
     * 不可监听写出不记录。请求在写出提交后释放，异常上抛路径同样释放。
     *
     * @param request        完整的 HTTP 请求，不能为 null
     * @param connectSession 当前连接会话，不能为 null，响应经其通道写出
     * @return 写出结果，永不为 null
     * @throws NullPointerException 任一参数为 null
     */
    public ResponseWriter.WriteResult doOrchestrate(FullHttpRequest request, ConnectSession connectSession) {
        return doOrchestrate(request, connectSession, System.nanoTime());
    }

    /**
     * 编排流程实体，起点时刻由调用方给定（{@link #orchestrate} 传入提交前取的时刻，
     * 使直调与经调度提交两路的耗时口径一致）。
     *
     * @param request        完整的 HTTP 请求
     * @param connectSession 当前连接会话
     * @param startTime      请求起始时刻（纳秒），请求日志耗时起算点
     * @return 写出结果，永不为 null
     */
    private ResponseWriter.WriteResult doOrchestrate(FullHttpRequest request, ConnectSession connectSession,
                                                     long startTime) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(connectSession, "connect session must not be null");
        try {
            HttpResponse response;
            if ("OPTIONS".equalsIgnoreCase(request.method().name())) {
                response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                handleCorsHeaders(request, response);
            } else {
                try {
                    response = startMiddleware(request, connectSession);
                } catch (Throwable throwable) {
                    Object handled = exceptionHandlerManager.handle(throwable);
                    response = resultConverterManager.convert(handled, defaultCharset);
                }
                handleCorsHeaders(request, response);
            }
            return attachRequestLog(request, startTime, responseWriterManager.write(response, connectSession));
        } finally {
            request.release();
        }
    }

    /**
     * 写出后挂接请求日志：写出可监听时先把请求方法取成字符串，再在完成凭据上注册回调，
     * 回调内以 DEBUG 级输出方法、URI、耗时（回调时刻减起点）与写出成败；
     * 回调触发时请求已释放，故回调不触碰请求对象。不可监听写出无完成凭据，
     * 不挂接、不记录。
     *
     * @param request     请求对象，挂接时尚未释放，仅用于取方法字面量
     * @param startTime   请求起始时刻（纳秒）
     * @param writeResult 写出结果
     * @return 原样返回写出结果，便于流程内联
     */
    private ResponseWriter.WriteResult attachRequestLog(FullHttpRequest request, long startTime,
                                                        ResponseWriter.WriteResult writeResult) {
        if (writeResult.writeType() == ResponseWriter.WriteType.LISTENABLE) {
            String method = request.method().name();
            String uri = request.uri();
            writeResult.channelFuture().addListener(future -> log.debug(
                    buildRequestLogMessage(method, uri, System.nanoTime() - startTime, future.isSuccess())));
        }
        return writeResult;
    }

    /**
     * CORS 来源处理：按请求的 Origin 与持有的 {@link HttpCorsConfig} 为响应写出 CORS 响应头。
     * <p>
     * 处理次序：Origin 头缺失或为空回落字面量 null；允许凭证时，来源为字面星号属
     * 标准禁止的组合、整体跳过不写任何头，否则先写出凭证许可头；来源通过放行门
     * （通配或精确白名单命中）时写出允许来源（回声实际值）、允许方法、允许请求头、
     * 可暴露响应头与预检缓存时长——各列表含通配项时写字面星号，否则逗号 join。
     * 来源未通过放行门时除凭证许可头外不写任何 CORS 头。
     * </p>
     *
     * @param request  请求对象，从其头域读取 Origin
     * @param response 响应对象，CORS 头直接写入其头域
     */
    public void handleCorsHeaders(FullHttpRequest request, HttpResponse response) {
        String origin = request.headers().get("Origin");
        if (origin == null || origin.isEmpty()) {
            origin = "null";
        }
        if (corsConfig.isAllowCredentials()) {
            if ("*".equals(origin)) {
                // CORS 标准禁止 Access-Control-Allow-Credentials 与 * 一起出现
                return;
            }
            response.headers().set("Access-Control-Allow-Credentials", "true");
        }
        if (corsConfig.isAllowedOrigin(origin)) {
            response.headers().set("Access-Control-Allow-Origin", origin);
            response.headers().set("Access-Control-Allow-Methods",
                    corsConfig.isAnyMethod() ? "*" : String.join(",", corsConfig.getAllowedMethods()));
            response.headers().set("Access-Control-Allow-Headers",
                    corsConfig.isAnyHeader() ? "*" : String.join(",", corsConfig.getAllowedHeaders()));
            List<String> exposedHeaders = corsConfig.getExposedHeaders();
            response.headers().set("Access-Control-Expose-Headers",
                    exposedHeaders.contains("*") ? "*" : String.join(",", exposedHeaders));
            response.headers().set("Access-Control-Max-Age", String.valueOf(corsConfig.getMaxAge()));
        }
    }

    /**
     * 启动中间件链：把链执行整体放入会话管理器的会话安全作用域——链执行期与会话
     * 垃圾回收的互斥由管理器在作用域内收口，作用域退出时自动释放；无共享状态的
     * 管理器实现直接透传执行回调。
     *
     * @param request        请求对象
     * @param connectSession 当前连接会话
     * @return 链结果经转化与善后产出的响应，不为 null
     */
    private HttpResponse startMiddleware(FullHttpRequest request, ConnectSession connectSession) {
        SessionManager sessionManager = sessionManagerHolder.getSessionManager();
        return sessionManager.sessionSafeScope(manager ->
                executeMiddleware(request, connectSession, manager));
    }

    /**
     * 执行中间件链并善后结果：初始化 Cookie 集合与会话，把请求转化为上下文的消息
     * （请求方式、请求头与请求地址）并按内容类型解析请求体得到内容对象，两者一并装配进
     * 请求上下文后调用链头，链结果经结果转化链转化为响应；随后回写 Cookie，会话新建或
     * 路径变更时设置 JSESSIONID 的 Set-Cookie 头。上下文释放与会话续期在返回前完成。
     * <p>HEAD 请求无请求体，内容侧以未声明类型的未知内容承载，不走解析器。
     * 请求体无效（内容类型命中解析器而体解析失败）时异常在装配上下文处抛出，
     * 与链内异常同径交由异常处理器处置。</p>
     *
     * @param request        请求对象
     * @param connectSession 当前连接会话
     * @param sessionManager 会话管理器
     * @return 链结果经转化与善后产出的响应，不为 null
     */
    private HttpResponse executeMiddleware(FullHttpRequest request, ConnectSession connectSession,
                                           SessionManager sessionManager) {
        HttpContext context = null;
        try {
            // 初始化Cookie
            HttpCookies cookies = new DefaultHttpCookies(request.headers());
            // 初始化session
            String originSessionId = cookies.getCookie("JSESSIONID");
            HttpSession httpSession = new DefaultHttpSession(sessionManager, originSessionId);
            // 请求对象在编排器内转化为上下文的对外契约：请求消息与请求体内容
            Http.Message message = new DefaultMessage(request, defaultCharset);
            Http.Content content = HttpMethod.HEAD == request.method()
                    ? new HttpUnknowContent(null)
                    : contentParserManager.parse(request);
            // 创建HttpContext对象
            context = new FullHttpContext(message, content, httpSession, cookies, connectSession, defaultCharset);
            // 执行中间件
            Object result = middleware.invoke(context);
            HttpResponse response = resultConverterManager.convert(result, defaultCharset);
            // 处理Cookie
            for (String setCookie : cookies.toSetCookies(defaultCharset)) {
                response.headers().add("Set-Cookie", setCookie);
            }
            // 处理session
            if (httpSession.sessionId() != null
                    && (!Objects.equals(httpSession.sessionId(), originSessionId) || httpSession.pathIsUpdate())) {
                response.headers().add("Set-Cookie",
                        "JSESSIONID=" + httpSession.sessionId() + "; Path=" + httpSession.getPath() + "; HttpOnly");
            }
            return response;
        } finally {
            if (context != null) {
                context.release();
                context.httpSession().expireAt();
            }
        }
    }

    /**
     * 构建 HTTP 429（Too Many Requests）响应：任务提交被虚拟线程资源拒绝时写出。
     *
     * @return 响应对象，正文为 "too many request"
     */
    private HttpResponse toManyRequestsResponse() {
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS);
        response.headers().add("Content-Type", "text/plain;charset=utf-8");
        String msg = "too many request";
        response.content().writeCharSequence(msg, StandardCharsets.UTF_8);
        response.headers().add("Content-Length", msg.length());
        return response;
    }

    /**
     * 构建请求日志文案：内容只用英文与 ASCII 字符，避免控制台编码乱码，
     * 依次为请求方法、URI、耗时与响应发送状态；耗时超过 1 毫秒（1000000 纳秒）
     * 按毫秒计，否则按微秒计，整除取商。
     *
     * @param method    请求方法名
     * @param uri       请求 URI
     * @param costNanos 执行耗时（纳秒）
     * @param isSuccess 响应是否成功发送
     * @return 渲染后的日志文案
     */
    static String buildRequestLogMessage(String method, String uri, long costNanos, boolean isSuccess) {
        String state = isSuccess ? "success" : "fail";
        if (costNanos > 1000000) {
            return method + " " + uri + " cost:" + (costNanos / 1000000) + "ms state:" + state;
        }
        return method + " " + uri + " cost:" + (costNanos / 1000) + "us state:" + state;
    }

    /**
     * {@link RequestOrchestrator} 的建造者：九个组件各一个流式配置方法，
     * 重复配置以最后一次为准；null 入参在配置方法即时拒绝，
     * 缺配在 {@link #build()} 拒绝且消息指名缺失组件。
     * {@link #build()} 可多次调用，产物互不影响。
     */
    public static class Builder {

        private Middleware middleware;
        private VirtualThreadResource virtualThreadResource;
        private ResponseWriterManager responseWriterManager;
        private ResultConverterManager resultConverterManager;
        private HttpCorsConfig corsConfig;
        private ExceptionHandlerManager exceptionHandlerManager;
        private SessionManagerHolder sessionManagerHolder;
        private Charset defaultCharset;
        private HttpContentParserManager contentParserManager;

        /** 设置中间件链链头。 */
        public Builder middleware(Middleware middleware) {
            this.middleware = Objects.requireNonNull(middleware, "middleware must not be null");
            return this;
        }

        /** 设置虚拟线程资源。 */
        public Builder virtualThreadResource(VirtualThreadResource virtualThreadResource) {
            this.virtualThreadResource = Objects.requireNonNull(virtualThreadResource, "virtual thread resource must not be null");
            return this;
        }

        /** 设置响应写出器管理器。 */
        public Builder responseWriterManager(ResponseWriterManager responseWriterManager) {
            this.responseWriterManager = Objects.requireNonNull(responseWriterManager, "response writer manager must not be null");
            return this;
        }

        /** 设置结果转化器管理器。 */
        public Builder resultConverterManager(ResultConverterManager resultConverterManager) {
            this.resultConverterManager = Objects.requireNonNull(resultConverterManager, "result converter manager must not be null");
            return this;
        }

        /** 设置 CORS 配置。 */
        public Builder corsConfig(HttpCorsConfig corsConfig) {
            this.corsConfig = Objects.requireNonNull(corsConfig, "cors config must not be null");
            return this;
        }

        /** 设置异常处理器管理器。 */
        public Builder exceptionHandlerManager(ExceptionHandlerManager exceptionHandlerManager) {
            this.exceptionHandlerManager = Objects.requireNonNull(exceptionHandlerManager, "exception handler manager must not be null");
            return this;
        }

        /** 设置会话管理器持有者。 */
        public Builder sessionManagerHolder(SessionManagerHolder sessionManagerHolder) {
            this.sessionManagerHolder = Objects.requireNonNull(sessionManagerHolder, "session manager holder must not be null");
            return this;
        }

        /** 设置缺省字符集（装配期由 HttpConfig 判定），随请求传入上下文，并用于响应转化与 Cookie 写出编码。 */
        public Builder defaultCharset(Charset defaultCharset) {
            this.defaultCharset = Objects.requireNonNull(defaultCharset, "default charset must not be null");
            return this;
        }

        /**
         * 设置请求体内容解析器管理器，装配上下文时由它按内容类型解析请求体。
         * <p>管理器由装配方建立：以缺省字符集与自定义解析器为构造参数，自定义解析器声明的
         * 类型与内置类型冲突（或自定义之间重复声明同一类型）在管理器构造期抛出
         * {@link IllegalArgumentException}，与本建造者无关。</p>
         *
         * @param contentParserManager 内容解析器管理器，不能为 null
         */
        public Builder contentParserManager(HttpContentParserManager contentParserManager) {
            this.contentParserManager = Objects.requireNonNull(contentParserManager, "content parser manager must not be null");
            return this;
        }

        /**
         * 产出编排器：九组件逐一校验齐备后装配。
         *
         * @return 以当前收集值装配的新编排器
         * @throws NullPointerException 存在未配置的组件，消息指名缺失者
         */
        public RequestOrchestrator build() {
            Objects.requireNonNull(middleware, "middleware must not be null");
            Objects.requireNonNull(virtualThreadResource, "virtual thread resource must not be null");
            Objects.requireNonNull(responseWriterManager, "response writer manager must not be null");
            Objects.requireNonNull(resultConverterManager, "result converter manager must not be null");
            Objects.requireNonNull(corsConfig, "cors config must not be null");
            Objects.requireNonNull(exceptionHandlerManager, "exception handler manager must not be null");
            Objects.requireNonNull(sessionManagerHolder, "session manager holder must not be null");
            Objects.requireNonNull(defaultCharset, "default charset must not be null");
            Objects.requireNonNull(contentParserManager, "content parser manager must not be null");
            return new RequestOrchestrator(this);
        }
    }
}
