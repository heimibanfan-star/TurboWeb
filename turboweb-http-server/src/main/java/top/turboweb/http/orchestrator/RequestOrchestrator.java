package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.concurrent.thread.MicroTaskExecutor;
import top.turboweb.http.context.*;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.cookie.DefaultHttpCookies;
import top.turboweb.http.cookie.HttpCookies;
import top.turboweb.http.session.DefaultHttpSession;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.session.SessionManager;
import top.turboweb.http.session.SessionManagerHolder;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.orchestrator.parser.HttpContentParserManager;
import top.turboweb.http.orchestrator.writer.ResponseWriter;
import top.turboweb.http.orchestrator.writer.ResponseWriterManager;
import top.turboweb.http.server.constant.ResourceNames;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.HttpRequestMethod;
import top.turboweb.http.core.RequestLine;
import top.turboweb.http.core.RequestTarget;
import top.turboweb.http.core.VoidRequestMessage;
import top.turboweb.http.request.FormRequestMessage;
import top.turboweb.http.request.JsonRequestMessage;
import top.turboweb.server.runtime.ResourceManager;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 请求编排器：以建造者装配九个协作组件——中间件链、微任务执行器、
 * 响应写出器管理器、结果转化器管理器、cors 配置、异常处理器管理器、
 * 会话管理器持有者、缺省字符集与请求体内容解析器管理器
 * （{@link HttpContentParserManager}，由装配方按缺省字符集与自定义解析器建立后传入）。
 * <p>
 * 组件全部 final、构造后不可变更；非空契约两级拒收：
 * 建造者配置方法对 null 入参即时拒绝，{@link Builder#build()} 对缺配组件拒绝并指名。
 * 磁盘IO线程池已在写出器管理器构造期装配，本类不直接持有；
 * 微任务执行器、中间件链与会话管理器持有者仅持有、不负责其生命周期。
 * </p>
 * <p>
 * 已实现的流程：{@link #orchestrate} 把请求任务提交到微任务执行器，
 * 任务未受理（资源耗尽、排队放弃或线程未能启动）时直接写出 HTTP 429 响应；
 * {@link #doOrchestrate} 在任务线程内执行完整编排——OPTIONS 预检不进中间件链、
 * 直接以空正文 200 携带 CORS 响应头应答；其余请求先转化为上下文的消息、
 * 按内容类型解析请求体得到内容对象，随后执行中间件链并经结果转化链产出响应，
 * 过程中抛出的异常交由异常处理器管理器产出处理结果后转化，响应统一经 CORS
 * 来源处理后写出。两个入口都在使用前自行持有请求引用、用完归还自己持有的那一份，
 * 不触碰调用方的引用：经 {@link #orchestrate} 提交时先 retain 自持，再由微任务执行器的
 * 收尾回调在每次提交的收尾处统一归还——任务执行完毕与任务未受理两种收尾
 * 必然触发且仅触发一次，未受理路径在回调内先写出 429 再归还；直接调用
 * {@link #doOrchestrate} 时同理，在响应写出后的收尾处归还自持引用。
 * 请求方法、URI 与来源头在流程入口快照，链执行后写 CORS 头与请求日志只读快照。
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
    /** 微任务执行器，承载请求任务的调度与限流。 */
    private final MicroTaskExecutor microTaskExecutor;
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
        // 微任务执行器作为内置资源按名从资源管理器获取，获取即保存到本类final字段
        this.microTaskExecutor = builder.resourceManager.getBuiltinResource(ResourceNames.MICRO_TASK_EXECUTOR, MicroTaskExecutor.class);
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
     * 编排请求入口：使用前先自行持有请求引用，再取起始时刻把请求提交到微任务执行器，
     * 获取资源后在任务线程内执行 {@link #doOrchestrate}；任务未受理时（资源耗尽且不允许
     * 排队、排队等待放弃或线程未能启动）不经中间件链与 CORS 处理，直接写出 HTTP 429 响应。
     * 正常与未受理两路写出均可监听时挂接完成回调打印 DEBUG 级请求日志，
     * 耗时自本入口起始时刻起算、含任务提交前的排队等待。
     * 请求引用由本入口 retain 自持：任务执行完毕与任务未受理两种收尾
     * 均由收尾回调统一归还这一份，未受理路径先写出 429 再归还；调用方的引用不受影响，
     * 由调用方自行归还。会话缺失则在本入口归还自持引用后提交前拒收。
     * 收尾回调的必然执行使提交入口无需自行兜底归还。
     *
     * @param request        完整的 HTTP 请求，不能为 null
     * @param connectSession 当前连接会话，不能为 null，响应经其通道写出
     * @throws NullPointerException 任一参数为 null
     */
    public void orchestrate(FullHttpRequest request, HttpConnection connectSession) {
        Objects.requireNonNull(request, "request must not be null");
        // 使用前自行持有引用：入站处理器在本次调用返回后即归还自己的引用
        request.retain();
        if (connectSession == null) {
            // 会话缺失属系统装配问题，提交前拒收：先归还本入口自持的引用再抛出
            request.release();
            throw new NullPointerException("connect session must not be null");
        }
        long startTime = System.nanoTime();
        microTaskExecutor.submit(
                () -> doOrchestrate(request, connectSession, startTime),
                signal -> {
                    try {
                        if (signal.finallyType() == MicroTaskExecutor.FinallyType.NOT_ACCEPT) {
                            attachRequestLog(request.method().name(), request.uri(), startTime,
                                    responseWriterManager.write(toManyRequestsResponse(), connectSession));
                        }
                    } finally {
                        // 执行完毕与未受理两路收尾统一归还本入口自持的引用
                        request.release();
                    }
                });
    }

    /**
     * 在当前线程执行完整编排流程并返回写出凭据：OPTIONS 请求以空正文 200
     * 携带 CORS 响应头直接应答、不进中间件链；其余请求先装配请求消息与内容，
     * 再执行中间件链并把结果经结果转化链转化为响应，
     * 链执行或转化过程中抛出的异常交由 {@link ExceptionHandlerManager#handle(Throwable)}
     * 产出处理结果后再转化；正常与异常响应统一经 CORS 来源处理写出 CORS 头后交
     * {@link ResponseWriterManager#write} 写出。请求引用由本入口使用前 retain 自持、
     * 链执行期间保持有效，在响应写出后的收尾处统一归还这一份，预检、正常与异常路径
     * 共用同一归还点；调用方的引用不受影响，由调用方自行归还。请求方法、URI 与来源头
     * 在流程入口快照，链执行后写 CORS 头与请求日志只读快照。
     * 写出可监听时挂接完成回调，
     * 以 DEBUG 级打印请求日志（方法、URI、自起点耗时、写出成败），展示与否由日志级别控制，
     * 不可监听写出不记录。
     *
     * @param request        完整的 HTTP 请求
     * @param connectSession 当前连接会话，响应经其通道写出
     * @return 写出结果，永不为 null
     */
    public ResponseWriter.WriteResult doOrchestrate(FullHttpRequest request, HttpConnection connectSession) {
        // 使用前自行持有引用，收尾只归还这一份，调用方的引用不受影响
        request.retain();
        try {
            return doOrchestrate(request, connectSession, System.nanoTime());
        } finally {
            request.release();
        }
    }

    /**
     * 编排流程实体，起点时刻由调用方给定（{@link #orchestrate} 传入提交前取的时刻，
     * 使直调与经调度提交两路的耗时口径一致）。
     * 请求对象在流程执行期间保持有效，引用由调用方负责持有与归还：经 {@link #orchestrate}
     * 提交时由微任务执行器的收尾回调归还自持引用，直接调用时由公开重载归还自持引用；
     * 本方法不 retain、不释放请求。
     *
     * @param request        完整的 HTTP 请求
     * @param connectSession 当前连接会话
     * @param startTime      请求起始时刻（纳秒），请求日志耗时起算点
     * @return 写出结果，永不为 null
     */
    private ResponseWriter.WriteResult doOrchestrate(FullHttpRequest request, HttpConnection connectSession,
                                                     long startTime) {
        // 请求行与来源头在流程入口快照：链执行后写 CORS 头与请求日志只读快照
        String method = request.method().name();
        String uri = request.uri();
        String origin = request.headers().get("Origin");
        HttpResponse response;
        if ("OPTIONS".equalsIgnoreCase(method)) {
            response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        } else {
            try {
                response = startMiddleware(request, connectSession);
            } catch (Throwable throwable) {
                Object handled = exceptionHandlerManager.handle(throwable);
                response = resultConverterManager.convert(handled, defaultCharset);
            }
        }
        handleCorsHeaders(origin, response);
        return attachRequestLog(method, uri, startTime, responseWriterManager.write(response, connectSession));
    }

    /**
     * 写出后挂接请求日志：写出可监听时在完成凭据上注册回调，回调内以 DEBUG 级输出
     * 方法、URI、耗时（回调时刻减起点）与写出成败；方法与地址为释放前取得的快照，
     * 回调触发时请求已释放，故回调不触碰请求对象。不可监听写出无完成凭据，
     * 不挂接、不记录。
     *
     * @param method      请求方法名快照
     * @param uri         请求 URI 快照
     * @param startTime   请求起始时刻（纳秒）
     * @param writeResult 写出结果
     * @return 原样返回写出结果，便于流程内联
     */
    private ResponseWriter.WriteResult attachRequestLog(String method, String uri, long startTime,
                                                        ResponseWriter.WriteResult writeResult) {
        if (writeResult.writeType() == ResponseWriter.WriteType.LISTENABLE) {
            writeResult.channelFuture().addListener(future -> log.debug(
                    buildRequestLogMessage(method, uri, System.nanoTime() - startTime, future.isSuccess())));
        }
        return writeResult;
    }

    /**
     * CORS 来源处理：从请求的来源头读取来源后，按
     * {@link #handleCorsHeaders(String, HttpResponse)} 的口径写出 CORS 响应头。
     *
     * @param request  请求对象，仅用于读取 Origin 头
     * @param response 响应对象，CORS 头直接写入其头域
     */
    public void handleCorsHeaders(FullHttpRequest request, HttpResponse response) {
        handleCorsHeaders(request.headers().get("Origin"), response);
    }

    /**
     * 从请求装配消息：构造期快照请求行（含按缺省字符集解码查询参数的请求目标）、
     * 把请求头集合拷贝为消息自有的可写存储，请求体内容由装配方解析产出后带入；
     * 装配返回后消息与请求头集合不再联动。
     *
     * <p>消息类按内容类型分派：内置内容对象配对应的强类型消息
     * （{@link JsonRequestMessage.JsonData} 配 {@link JsonRequestMessage}、
     * {@link FormRequestMessage.FormData} 配 {@link FormRequestMessage}），
     * 内容为 {@code null} 装配无体消息 {@link VoidRequestMessage}，
     * 其余内容（自定义解析器产出等）装配泛型请求消息。</p>
     *
     * @param request      请求对象，不能为空
     * @param content      请求体内容，无请求体或未命中解析器时为 {@code null}
     * @param queryCharset 查询参数解码的缺省字符集，不能为空
     * @return 请求消息
     * @throws NullPointerException     当 {@code request} 或 {@code queryCharset} 为 {@code null} 时抛出
     * @throws IllegalArgumentException 请求行的方法字面量不在标准方法范围内时抛出
     */
    public static HttpRequestMessage<?> assembleMessage(FullHttpRequest request, Object content, Charset queryCharset) {
        Objects.requireNonNull(request, "request can not be null");
        RequestLine requestLine = new RequestLine(HttpRequestMethod.valueOf(request.method().name()),
                new RequestTarget(request.uri(), queryCharset));
        HttpRequestMessage<?> message;
        switch (content) {
            case String jsonString -> message = new JsonRequestMessage(requestLine, jsonString);
            case FormRequestMessage.FormData formData -> message = new FormRequestMessage(requestLine, formData);
            case null -> message = new VoidRequestMessage(requestLine);
            default -> message = new HttpRequestMessage<>(requestLine, content);
        }
        for (Map.Entry<String, String> entry : request.headers()) {
            message.addHeader(entry.getKey(), entry.getValue());
        }
        return message;
    }

    /**
     * CORS 来源处理：按来源头快照与持有的 {@link HttpCorsConfig} 为响应写出 CORS 响应头。
     * <p>
     * 处理次序：来源头缺失或为空回落字面量 null；允许凭证时，来源为字面星号属
     * 标准禁止的组合、整体跳过不写任何头，否则先写出凭证许可头；来源通过放行门
     * （通配或精确白名单命中）时写出允许来源（回声实际值）、允许方法、允许请求头、
     * 可暴露响应头与预检缓存时长——各列表含通配项时写字面星号，否则逗号 join。
     * 来源未通过放行门时除凭证许可头外不写任何 CORS 头。
     * </p>
     *
     * @param origin   请求来源头快照，可为 null
     * @param response 响应对象，CORS 头直接写入其头域
     */
    private void handleCorsHeaders(String origin, HttpResponse response) {
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
     * 启动中间件链：在会话管理器的会话安全作用域内完成一次请求处理——请求装配、
     * 会话初始化、链执行、Cookie 回写与会话续期均在作用域内完成，与垃圾回收的
     * 互斥由管理器在作用域内收口，作用域退出时自动释放；无共享状态的管理器实现
     * 直接透传执行回调。
     *
     * @param request        请求对象，由提交入口统一释放，本方法内不归还
     * @param connectSession 当前连接会话
     * @return 链结果经转化与善后产出的响应，不为 null
     */
    private HttpResponse startMiddleware(FullHttpRequest request, HttpConnection connectSession) {
        SessionManager sessionManager = sessionManagerHolder.getSessionManager();
        return sessionManager.sessionSafeScope(manager ->
                executeMiddleware(request, connectSession, manager));
    }

    /**
     * 装配请求并在会话安全作用域内执行中间件链：初始化 Cookie 集合并读取会话标识，
     * 按内容类型解析请求体得到内容对象，再连同请求装配为请求消息（请求方式、请求头、
     * 请求地址与请求体内容）；以装配产物初始化会话与请求上下文，调用链头，链结果经
     * 结果转化链转化为响应；随后回写 Cookie，会话新建或路径变更时设置 JSESSIONID
     * 的 Set-Cookie 头。请求对象在链执行期间保持有效，由提交入口统一释放，
     * 本方法不归还请求。
     * <p>内容资源的关闭在方法退出时无条件执行，与装配、会话初始化、链执行同处一个
     * 异常边界：装配成功后的任何异常都不会使内容逃过关闭。资源释放是创建方
     * （本编排器）的职责，请求上下文不承载释放入口。</p>
     * <p>HEAD 请求无请求体，内容侧以未声明类型的未知内容承载，不走解析器；
     * 请求体无效（内容类型命中解析器而体解析失败）时异常在装配阶段抛出，
     * 与链内异常同径交由异常处理器处置。</p>
     *
     * @param request        请求对象，不能为 null；其释放归提交入口，本方法不归还
     * @param connectSession 当前连接会话
     * @param sessionManager 会话管理器
     * @return 链结果经转化与善后产出的响应，不为 null
     */
    private HttpResponse executeMiddleware(FullHttpRequest request, HttpConnection connectSession,
                                           SessionManager sessionManager) {
        Object content = null;
        HttpSession httpSession = null;
        try {
            HttpCookies cookies;
            String originSessionId;
            HttpRequestMessage<?> message;
            // 初始化Cookie
            cookies = new DefaultHttpCookies(request.headers());
            originSessionId = cookies.getCookie("JSESSIONID");
            // 请求对象在编排器内转化为上下文的对外契约：请求体内容先就位，再连同请求装配为请求消息；
            // HEAD 请求不携带请求体，内容留 null 装配为无体消息
            content = HttpMethod.HEAD == request.method()
                    ? null
                    : contentParserManager.parse(request);
            message = assembleMessage(request, content, defaultCharset);
            // 初始化session
            httpSession = new DefaultHttpSession(sessionManager, originSessionId);
            // 创建HttpContext对象
            HttpContext context = new FullHttpContext(message, httpSession, cookies,
                    connectSession, defaultCharset);
            // 执行中间件
            Object result = middleware.invoke(context);
            HttpResponse response = resultConverterManager.convert(result, defaultCharset);
            // 处理Cookie
            for (String setCookie : cookies.toSetCookies(defaultCharset)) {
                response.headers().add(HttpHeaderNames.SET_COOKIE, setCookie);
            }
            // 处理session
            if (httpSession.sessionId() != null
                    && (!Objects.equals(httpSession.sessionId(), originSessionId)
                    || httpSession.pathIsUpdate())) {
                response.headers().add("Set-Cookie",
                        "JSESSIONID=" + httpSession.sessionId() + "; Path=" + httpSession.getPath() + "; HttpOnly");
            }
            return response;
        } finally {
            // 内容自产出起即由本方法负责关闭，装配成功后的异常位置全部落在此边界内
            closeContentQuietly(content);
            if (httpSession != null) {
                httpSession.expireAt();
            }
        }
    }

    /**
     * 静默关闭内容对象持有的资源：内容实现 {@link AutoCloseable}（持有解码器、直接内存或
     * 磁盘临时文件等需归还资源）时调用其 {@link AutoCloseable#close()}；关闭抛出的异常
     * 只记录不外抛，不影响请求其余环节的收尾。内容不持有资源时为空操作，重复调用无副作用。
     *
     * @param content 内容解析与装配阶段产出的内容对象，可为 null（装配在产出内容前失败），
     *                为 null 时为空操作
     */
    private void closeContentQuietly(Object content) {
        if (content instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                log.error("Failed to close http content", e);
            }
        }
    }

    /**
     * 构建 HTTP 429（Too Many Requests）响应：任务提交未受理时写出。
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
        private ResourceManager resourceManager;
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

        /** 设置资源管理器，微任务执行器按 {@link ResourceNames#MICRO_TASK_EXECUTOR} 从其中获取。 */
        public Builder resourceManager(ResourceManager resourceManager) {
            this.resourceManager = Objects.requireNonNull(resourceManager, "resource manager must not be null");
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
            Objects.requireNonNull(resourceManager, "resource manager must not be null");
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
