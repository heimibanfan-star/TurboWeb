package top.turboweb.gateway;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import top.turboweb.gateway.breaker.Breaker;
import top.turboweb.gateway.breaker.EmptyBreaker;
import top.turboweb.gateway.loadbalance.LoadBalancer;
import top.turboweb.gateway.loadbalance.LoadBalancerFactory;
import top.turboweb.gateway.node.Node;
import top.turboweb.gateway.rule.Rule;
import top.turboweb.gateway.rule.RuleDetail;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于实现网关的channelHandler
 */
@ChannelHandler.Sharable
public class GatewayChannelHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String TURBOWEB_GATEWAY_HEADER = "TurboWeb-Forward";
    private static final Logger log = LoggerFactory.getLogger(GatewayChannelHandler.class);
    private final LoadBalancer loadBalancer;
    private volatile Rule rule;
    private HttpClient httpClient;
    private final Breaker breaker;

    public GatewayChannelHandler(LoadBalancerFactory loadBalancerFactory) {
        this.loadBalancer = loadBalancerFactory.createLoadBalancer();
        this.breaker = new EmptyBreaker();
    }

    public GatewayChannelHandler(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
        this.breaker = new EmptyBreaker();
    }

    public GatewayChannelHandler(LoadBalancer loadBalancer, Breaker breaker) {
        this.loadBalancer = loadBalancer;
        this.breaker = breaker;
    }

    public GatewayChannelHandler(Breaker breaker) {
        this.loadBalancer = LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer();
        this.breaker = breaker;
    }

    public GatewayChannelHandler() {
        this.loadBalancer = LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer();
        this.breaker = new EmptyBreaker();
    }

    /**
     * 设置HttpClient
     *
     * @param httpClient HttpClient
     */
    public void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient can not be null");
        if (this.httpClient == null) {
            this.httpClient = httpClient.responseTimeout(Duration.ofMillis(breaker.getTimeout()));
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        request.retain();
        Rule rule = this.rule;
        // 网关失效逻辑
        if (rule == null) {
            ctx.writeAndFlush(errorResponse("Gateway is not available"));
            return;
        }

        // 判断当前请求是否被转发
        if (request.headers().contains(TURBOWEB_GATEWAY_HEADER)) {
            // 判断是否允许当前节点处理
            RuleDetail detail = rule.getLocalService(request.uri());
            if (detail == null) {
                ctx.writeAndFlush(errorResponse("Service not found"));
            } else {
                handleRequestLocal(ctx, request, detail);
            }
            return;
        }

        // 正常节点尝试匹配
        RuleDetail detail = rule.getService(request.uri());
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
        String newUri = request.uri().replaceFirst(detail.rewriteRegex(), detail.rewriteTarget());
        FullHttpRequest fullHttpRequest = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), newUri, request.content());
        fullHttpRequest.headers().set(request.headers());
        ctx.fireChannelRead(fullHttpRequest);
    }

    private void handleRequestRemote(ChannelHandlerContext ctx, FullHttpRequest request, RuleDetail detail) {
        // 匹配节点
        Node node = loadBalancer.loadBalance(detail.serviceName());
        if (node == null) {
            ctx.writeAndFlush(errorResponse(detail.serviceName() + " has no nodes available"));
            return;
        }
        String newUri = request.uri().replaceFirst(detail.rewriteRegex(), detail.rewriteTarget());
        String fullUrl = detail.protocol() + "://" + node.url() + detail.extPath() + newUri;
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
                        empty -> {},
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

    public void setRule(Rule rule) {
        Objects.requireNonNull(rule, "rule can not be null");
        if (rule.isUsed()) {
            this.rule = rule;
        }
    }
}
