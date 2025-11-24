package top.turboweb.gateway;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import jakarta.validation.constraints.NotNull;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.gateway.fail.NodeMatchFailStrategy;
import top.turboweb.loadbalance.LoadBalancer;
import top.turboweb.loadbalance.LoadBalancerFactory;
import top.turboweb.loadbalance.breaker.Breaker;
import top.turboweb.loadbalance.breaker.EmptyBreaker;
import top.turboweb.gateway.filter.*;
import top.turboweb.loadbalance.node.Node;
import top.turboweb.loadbalance.rule.RuleManager;
import top.turboweb.loadbalance.rule.RuleDetail;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TurboWeb 网关核心处理器。
 * <p>
 * 此类继承自 Netty 的 {@link SimpleChannelInboundHandler}，
 * 用于处理来自客户端的 {@link FullHttpRequest}，
 * 并根据规则转发到本地服务或远程节点。
 *
 * <p>功能包括：
 * <ul>
 *     <li>请求过滤器链执行（同步/异步）</li>
 *     <li>负载均衡节点选择与转发</li>
 *     <li>WebSocket 协议升级与数据中继</li>
 *     <li>熔断与超时控制</li>
 * </ul>
 *
 * @param <FT> 过滤器执行类型（同步为 Boolean，异步为 Mono&lt;Boolean&gt;）
 */
@ChannelHandler.Sharable
public class GatewayChannelHandler<FT> extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String TURBOWEB_GATEWAY_HEADER = "TurboWeb-Forward";
    private static final InternalLogger log = InternalLoggerFactory.getInstance(GatewayChannelHandler.class);

    /** 网关过滤器上下文，负责执行过滤链 */
    private final GatewayFilterContext<FT> gatewayFilterContext;

    /** 负载均衡器，用于选择可用节点 */
    private final LoadBalancer loadBalancer;

    /** 路由与规则管理器 */
    private volatile RuleManager ruleManager;

    /** Reactor Netty 的 HTTP 客户端，用于远程转发 */
    private HttpClient httpClient;

    /** 熔断器，用于处理超时、失败率过高的节点 */
    private final Breaker breaker;

    private final NodeMatchFailStrategy nodeMatchFailStrategy;

    @NotNull
    private JsonSerializer jsonSerializer = new JacksonJsonSerializer();


    /** 创建同步版网关处理器 */
    public static GatewayChannelHandler<Boolean> create() {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                new EmptyBreaker(),
                new SyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Boolean> create(LoadBalancerFactory loadBalancerFactory) {
        return new GatewayChannelHandler<>(
                loadBalancerFactory.createLoadBalancer(),
                new EmptyBreaker(),
                new SyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Boolean> create(LoadBalancer loadBalancer) {
        return new GatewayChannelHandler<>(
                loadBalancer,
                new EmptyBreaker(),
                new SyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Boolean> create(Breaker breaker) {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                breaker,
                new SyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Boolean> create(LoadBalancer loadBalancer, Breaker breaker) {
        return new GatewayChannelHandler<>(
                loadBalancer,
                breaker,
                new SyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Boolean> create(Breaker breaker, NodeMatchFailStrategy nodeMatchFailStrategy) {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                breaker,
                new SyncGatewayFilterContext(),
                nodeMatchFailStrategy
        );
    }

    /** 创建异步版网关处理器 */
    public static GatewayChannelHandler<Mono<Boolean>> createAsync() {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                new EmptyBreaker(),
                new AsyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Mono<Boolean>> createAsync(LoadBalancerFactory loadBalancerFactory) {
        return new GatewayChannelHandler<>(
                loadBalancerFactory.createLoadBalancer(),
                new EmptyBreaker(),
                new AsyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Mono<Boolean>> createAsync(LoadBalancer loadBalancer) {
        return new GatewayChannelHandler<>(
                loadBalancer,
                new EmptyBreaker(),
                new AsyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Mono<Boolean>> createAsync(Breaker breaker) {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                breaker,
                new AsyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Mono<Boolean>> createAsync(LoadBalancer loadBalancer, Breaker breaker) {
        return new GatewayChannelHandler<>(
                loadBalancer,
                breaker,
                new AsyncGatewayFilterContext(),
                NodeMatchFailStrategy.REJECT
        );
    }

    public static GatewayChannelHandler<Mono<Boolean>> createAsync(Breaker breaker, NodeMatchFailStrategy nodeMatchFailStrategy) {
        return new GatewayChannelHandler<>(
                LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer(),
                breaker,
                new AsyncGatewayFilterContext(),
                nodeMatchFailStrategy
        );
    }

    private GatewayChannelHandler(
            LoadBalancer loadBalancer,
            Breaker breaker,
            GatewayFilterContext<FT> gatewayFilterContext,
            NodeMatchFailStrategy nodeMatchFailStrategy
    ) {
        Objects.requireNonNull(loadBalancer, "loadBalancer can not be null");
        Objects.requireNonNull(breaker, "breaker can not be null");
        Objects.requireNonNull(gatewayFilterContext, "gatewayFilterContext can not be null");
        Objects.requireNonNull(nodeMatchFailStrategy, "nodeMatchFailStrategy can not be null");
        this.loadBalancer = loadBalancer;
        this.breaker = breaker;
        this.gatewayFilterContext = gatewayFilterContext;
        this.nodeMatchFailStrategy = nodeMatchFailStrategy;
    }


    /**
     * 设置 Reactor Netty HttpClient。
     * 该客户端会根据 Breaker 的超时设置配置响应超时时间。
     */
    public void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient can not be null");
        if (this.httpClient == null) {
            this.httpClient = httpClient.responseTimeout(Duration.ofMillis(breaker.getTimeout()));
        }
    }

    /**
     * 设置 Json 序列化器。
     * 默认使用 JacksonJsonSerializer。
     */
    public void setJsonSerializer(JsonSerializer jsonSerializer) {
        Objects.requireNonNull(jsonSerializer, "jsonSerializer can not be null");
        this.jsonSerializer = jsonSerializer;
    }

    /**
     * 向网关中添加过滤器。
     * @param filter 自定义过滤器
     * @return 当前处理器实例（链式调用）
     */
    public GatewayChannelHandler<FT> addFilter(GatewayFilter<FT> filter) {
        gatewayFilterContext.addFilter(filter);
        return this;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        ChannelPromise promise = ctx.newPromise();
        ResponseHelper helper = new DefaultResponseHelper(ctx, jsonSerializer);
        gatewayFilterContext.startFilter(request, helper, promise);
        // 增加引用
        request.retain();
        promise.addListener(future -> {
            try {
                if (future.isSuccess()) {
                    // 执行节点的匹配逻辑
                    this.doMatch(ctx, request);
                } else {
                    if (!helper.isResponse()) {
                        ctx.writeAndFlush(errorResponse(future.cause().getMessage()));
                    }
                }
            } finally {
                request.release();
            }
        });

    }


    /**
     * 网关核心的服务匹配逻辑。
     *
     * <p>步骤说明：</p>
     * <ol>
     *     <li><b>获取规则快照：</b> 从当前 RuleManager 获取路由规则；若规则未初始化则网关不可用。</li>
     *
     *     <li><b>判断是否为二次转发（内部调用）：</b><br>
     *         若请求头中包含 {@code TurboWeb-Forward}，表示该请求已经从其他节点转发过来，
     *         此时仅允许本节点匹配本地服务：
     *         <ul>
     *             <li>尝试通过 {@code ruleManager.getLocalService()}</li>
     *             <li>若匹配失败，则进入 {@code nodeMatchFailStrategy} 降级逻辑</li>
     *             <li>最终若仍不能匹配，则直接返回 502 错误</li>
     *         </ul>
     *     </li>
     *
     *     <li><b>普通请求匹配：</b><br>
     *         非二次转发的请求会进入标准路由流程：
     *         <ul>
     *             <li>优先使用 {@code ruleManager.getService(uri)} 根据完整路由规则匹配服务</li>
     *             <li>若匹配失败，则使用降级策略尝试提供备用规则</li>
     *         </ul>
     *     </li>
     *
     *     <li><b>决定请求的处理方式：</b>
     *         <ul>
     *             <li>若匹配的规则为 local=true → 执行本地转发 {@link #handleRequestLocal}</li>
     *             <li>否则 → 执行远程节点转发 {@link #handleRequestRemote}</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * <p>整体而言，该方法承担了网关的核心决策功能：判断当前请求应该由本节点处理还是由其他节点处理，
     * 并结合降级策略保障在规则缺失或路由失败时仍尽可能提供 fallback 能力。</p>
     *
     * @param ctx     ChannelHandlerContext
     * @param request FullHttpRequest 当前客户端请求
     */
    private void doMatch(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 拿到规则管理器的快照
        RuleManager ruleManager = this.ruleManager;
        // 网关失效逻辑
        if (ruleManager == null) {
            ctx.writeAndFlush(errorResponse("Gateway is not available"));
            return;
        }
        // 判断当前请求是否被转发
        if (request.headers().contains(TURBOWEB_GATEWAY_HEADER)) {
            // 判断是否允许当前节点处理
            RuleDetail detail = ruleManager.getLocalService(request.uri());
            // 降级再次尝试获取规则
            if (detail == null) {
                detail = this.nodeMatchFailStrategy.onMatchFail();
            }
            // 判断是否能拿到对应的规则
            if (detail == null) {
                ctx.writeAndFlush(errorResponse("Service not found"));
            } else {
                handleRequestLocal(ctx, request, detail);
            }
            return;
        }

        // 正常节点尝试匹配
        RuleDetail detail = ruleManager.getService(request.uri());
        // 降级再次尝试获取规则
        if (detail == null) {
            detail = this.nodeMatchFailStrategy.onMatchFail();
        }
        if (detail == null) {
            ctx.writeAndFlush(errorResponse("Service not found"));
            return;
        }
        // 判断节点需要本地处理还是远程处理
        if (detail.local()) {
            handleRequestLocal(ctx, request, detail);
        } else {
            handleRequestRemote(ctx, request, detail);
        }
    }

    /**
     * 处理本地请求
     *
     * @param ctx     ChannelHandlerContext
     * @param request FullHttpRequest
     * @param detail  规则详情
     */
    private void handleRequestLocal(ChannelHandlerContext ctx, FullHttpRequest request, RuleDetail detail) {
        // 增加引用
        request.retain();
        String newUri = request.uri().replaceFirst(detail.rewriteRegex(), detail.rewriteTarget());
        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), newUri, request.content());
        fullHttpRequest.headers().set(request.headers());
        ctx.fireChannelRead(fullHttpRequest);
    }

    private void handleRequestRemote(ChannelHandlerContext ctx, FullHttpRequest request, RuleDetail detail) {
        // 增加引用
        request.retain();
        // 匹配节点
        Node node = loadBalancer.loadBalance(detail.serviceName());
        if (node == null) {
            ctx.writeAndFlush(errorResponse(detail.serviceName() + " has no nodes available"));
            return;
        }
        String newUri = request.uri().replaceFirst(detail.rewriteRegex(), detail.rewriteTarget());
        String fullUrl = detail.protocol().getProtocol() + "://" + node.url() + detail.extPath() + newUri;
        // 判断是否需要升级为websocket
        if (Objects.equals(request.headers().get(HttpHeaderNames.UPGRADE), "websocket")) {
            forwardWebSocket(ctx, request, node, fullUrl);
        } else {
            // 转发请求
            forwardHttp(ctx, request, node, fullUrl);
        }
    }

    /**
     * 转发websocket请求
     *
     * @param ctx     ChannelHandlerContext
     * @param request FullHttpRequest
     * @param node    节点
     */
    private void forwardWebSocket(ChannelHandlerContext ctx, FullHttpRequest request, Node node, String fullUrl) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (pipeline.get(WebSocketServerProtocolHandler.class) != null) {
            return;
        }
        // 删除所有的后续处理器
        removeAfterHandler(ctx);
        // 添加websocket相关的处理器
        pipeline.addLast(new WebSocketServerProtocolHandler(request.uri()));
        // 创建Flux流接收websocket的帧
        Flux<WebSocketFrame> webSocketFrameFlux = Flux.create(sink -> {
            pipeline.addLast(new SimpleChannelInboundHandler<WebSocketFrame>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
                    msg.retain();
                    sink.next(msg);
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    sink.complete();
                    super.channelInactive(ctx);
                }
            });
        });
        Promise<Void> closePromise = ctx.channel().newPromise();
        // 创建远程节点的websocket连接
        Disposable remoteDisposable = httpClient.websocket()
                .uri(fullUrl)
                .handle((inbound, outbound) -> {
                    // 向远程节点发送消息
                    Mono<Void> send = webSocketFrameFlux.flatMap(frame -> switch (frame) {
                                case TextWebSocketFrame textWebSocketFrame ->
                                        outbound.sendString(Mono.just(textWebSocketFrame.text()));
                                case BinaryWebSocketFrame binaryWebSocketFrame ->
                                        outbound.send(Mono.just(binaryWebSocketFrame.content()));
                                case CloseWebSocketFrame ignored -> outbound.sendClose();
                                case null, default -> Mono.empty();
                            })
                            .then();
                    Mono<Void> receive = inbound
                            .receiveFrames()
                            .doOnNext(frame -> {
                                frame.retain();
                                ctx.writeAndFlush(frame);
                            })
                            .then();
                    return Mono.firstWithSignal(send, receive);
                })
                .subscribe(
                        empty -> {
                        },
                        closePromise::setFailure,
                        () -> closePromise.setSuccess(null)
                );
        closePromise.addListener(future -> {
            if (ctx.channel().isActive()) {
                ctx.close();
            }
            if (!remoteDisposable.isDisposed()) {
                remoteDisposable.dispose();
            }
        });
        ctx.fireChannelRead(request);
    }

    /**
     * 移除后续处理器
     *
     * @param ctx ChannelHandlerContext
     */
    private void removeAfterHandler(ChannelHandlerContext ctx) {
        ChannelPipeline pipeline = ctx.pipeline();
        while (pipeline.last() != this) {
            pipeline.remove(pipeline.last());
        }
    }

    /**
     * 转发请求
     *
     * @param ctx             ChannelHandlerContext
     * @param fullHttpRequest FullHttpRequest
     * @param node            节点
     */
    private void forwardHttp(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Node node, String targetUrl) {
        // 判断当前请求是否被熔断
        if (!breaker.isAllow(fullHttpRequest.uri())) {
            ctx.writeAndFlush(errorResponse("service " + fullHttpRequest.uri() + " is break"));
            return;
        }
        AtomicBoolean sendStarted = new AtomicBoolean(false);
        // 移交远程节点
        fullHttpRequest.headers().add(TURBOWEB_GATEWAY_HEADER, "true");
        httpClient.request(fullHttpRequest.method())
                .uri(targetUrl)
                .send((request, outbound) -> {
                    request.headers(fullHttpRequest.headers());
                    return outbound.send(Mono.just(fullHttpRequest.content()));
                })
                .response((response, content) -> {
                    // 判断是否请求成功
                    int statusCode = response.status().code();
                    if (breaker.failStatusCode().contains(statusCode)) {
                        breaker.setFail(fullHttpRequest.uri());
                    } else {
                        breaker.setSuccess(fullHttpRequest.uri());
                    }
                    // 写入响应头
                    HttpResponse toWriteResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status());
                    toWriteResponse.headers().set(response.responseHeaders());
                    // 写入响应
                    ctx.writeAndFlush(toWriteResponse);
                    sendStarted.set(true);
                    return content;
                })
                .map(DefaultHttpContent::new)
                .subscribe(
                        content -> {
                            content.retain();
                            ctx.writeAndFlush(content);
                        },
                        err -> {
                            log.error("Error when forwarding request to remote node", err);
                            if (!sendStarted.get()) {
                                ctx.writeAndFlush(errorResponse(err.getMessage())).addListener(f -> {
                                    ctx.close();
                                });
                            } else {
                                ctx.close();
                            }
                            // 设置短路失败
                            breaker.setFail(fullHttpRequest.uri());
                        },
                        () -> {
                            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                        }
                );


    }

    private HttpResponse errorResponse(String message) {
        String html = """
                <h1>TurboWeb GateWay Error</h1>
                code:%d, msg: %s
                """.formatted(HttpResponseStatus.BAD_GATEWAY.code(), message);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY);
        response.content().writeBytes(html.getBytes());
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=utf-8");
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    /**
     * 添加服务
     *
     * @param serviceName 服务名
     * @param urls        服务地址
     */
    public void addService(String serviceName, String... urls) {
        loadBalancer.addServices(serviceName, urls);
    }

    /**
     * 重置服务
     *
     * @param servicesNodes 服务节点
     */
    public void resetServices(Map<String, Set<String>> servicesNodes) {
        loadBalancer.resetServiceNodes(servicesNodes);
    }

    public void setRule(RuleManager ruleManager) {
        Objects.requireNonNull(ruleManager, "rule can not be null");
        if (ruleManager.used()) {
            this.ruleManager = ruleManager;
        }
    }
}
