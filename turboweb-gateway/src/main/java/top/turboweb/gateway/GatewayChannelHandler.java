package top.turboweb.gateway;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import top.turboweb.gateway.loadbalance.LoadBalancer;
import top.turboweb.gateway.loadbalance.LoadBalancerFactory;
import top.turboweb.gateway.node.Node;
import top.turboweb.gateway.rule.Rule;
import top.turboweb.gateway.rule.RuleDetail;

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

    public GatewayChannelHandler(LoadBalancerFactory loadBalancerFactory) {
        this.loadBalancer = loadBalancerFactory.createLoadBalancer();
    }

    public GatewayChannelHandler(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public GatewayChannelHandler() {
        this.loadBalancer = LoadBalancerFactory.RIBBON_LOAD_BALANCER.createLoadBalancer();
    }

    /**
     * 设置HttpClient
     *
     * @param httpClient HttpClient
     */
    public void setHttpClient(HttpClient httpClient) {
        Objects.requireNonNull(httpClient, "httpClient can not be null");
        if (this.httpClient == null) {
            this.httpClient = httpClient;
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
        String fullUrl = detail.protocol() + "://" +  node.url() + detail.extPath() + newUri;
        // 转发请求
        forwardHttp(ctx, request, node, fullUrl);
    }

    /**
     * 转发websocket请求
     *
     * @param ctx     ChannelHandlerContext
     * @param request FullHttpRequest
     * @param node    节点
     */
    private void forwardWebSocket(ChannelHandlerContext ctx, FullHttpRequest request, Node node) {
        ctx.writeAndFlush(errorResponse("websocket is not supported"));
    }

    /**
     * 转发请求
     *
     * @param ctx             ChannelHandlerContext
     * @param fullHttpRequest FullHttpRequest
     * @param node            节点
     */
    private void forwardHttp(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest, Node node, String targetUrl) {
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
