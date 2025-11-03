# 嵌入式网关

你的总结非常准确，TurboWeb 的嵌入式网关机制**适用于服务较少、网络拓扑简单的场景**，但在大规模微服务系统中确实可能存在维护复杂度高、路由同步不易等问题。下面是对你这段内容的专业化润色版本，提升整体表达的严谨性、逻辑性与技术深度，同时补充对架构优势与边界的说明。

**_核心优势_**

✅ 原生集成，开箱即用：网关能力作为 TurboWeb 协议调度器的一部分，直接集成在框架内部，零配置启动，开发者只需定义路由映射即可使用。

✅ SSE 转发支持：除常规 HTTP 请求外，还原生支持 SSE（Server-Sent Events） 的透传与多节点流式分发。

✅ 极低资源占用：基于异步 IO 与事件驱动模型，性能开销远低于传统反向代理或独立网关进程。

✅ 无中心架构，天然容灾：每个服务节点都具备独立路由能力，可有效规避中心化网关的单点故障问题，提升系统的容错性与可用性。

✅ 支持WebSocket的透传，通过双向Reactor Stream实现WebSocket的中继转发。

**_当前限制_**

⚠️ 适用于服务规模有限场景：节点之间需手动维护或共享路由规则，当服务数量较多或拓扑关系复杂时，配置和同步成本较高，建议结合服务注册中心或独立网关使用。

⛔ 不支持多协议聚合、链路编排等高级网关能力。

**_典型的应用场景_**

多模块服务统一入口：适用于单体拆分后的多模块项目，可在任意节点访问所有功能，简化部署和调试流程。

轻量级边缘节点网关：可作为边缘部署节点的前置入口，将请求代理到后端服务，无需额外部署如 Nginx、Envoy 等反向代理。

开发/测试阶段服务聚合：开发环境中快速聚合多个服务，无需注册中心或独立网关即可模拟完整调用链。

## 快速上手

以下示例展示如何将两个服务节点（用户模块与订单模块）互为网关，实现跨节点访问能力。

**_创建两个Controller_**

```java
@Route("/user")
public class UserController {   
    @Get
    public String getUser() {
        return "get user";
    }
}

@Route("/order")
public class OrderController {
    @Get
    public String getOrder() {
        return "get order";
    }
}
```

**_创建启动类并配置网关_**

用户服务节点：

```java
public class UserApplication {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new UserController());

        // 创建嵌入式网关
        GatewayChannelHandler<Boolean> gateway = GatewayChannelHandler.create();
        // 注册服务节点
        gateway.addService("orderService", "localhost:8081");
        // 配置映射规则
        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/order/**", "http://orderService");
        // 注册本地节点
        ruleManager.addRule("/api/user/**", "http://local", "/api", "");
        gateway.setRule(ruleManager);

        // 启动服务器并注册网关
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .gatewayHandler(gateway)
                .start(8080);
    }
}
```

订单服务节点：

```java
public class OrderApplication {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager(true);
        routerManager.addController(new OrderController());
        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and()
                .start(8081);
    }
}

```

**_访问验证_**

```http
GET http://localhost:8080/user
```

```http
GET http://localhost:8080/order
```

可以发现，通过User既可以访问到本地节点也可以访问到远程节点。

**代码解释**

服务的信息通过 `GatewayChannelHandler` 的 addService方法注册微服务信息，一般可以配合注册中心，例如Nacos使用，需要注意的是在注册服务节点时不能写协议部分，协议应该交给映射规则来设置。

`NodeRuleManager` 的 addRule方法用于配置映射规则，这是一个简化的版本，第一个参数是路径表达式，第二个参数是服务表达式。路径表达式支持通配符，例如*或者**，而服务表达式需要满足格式，协议://服务名，如果是转发到本地，服务名为`local`。

**路径重写**

TurboWeb的嵌入式网关支持路径重写，例如下面的请求：

```http
http://localhost:8080/api/user
```

我们知道，在我们之前设计的路径中，请求地址应该是http://localhost:8080/user，而不是http://localhost:8080/api/user，因此正常访问应该找不到，这个时候我们就需要借助网关的功能进行路径重写了，例如下面代码：

```java
ruleManager.addRule("/api/user/**", "http://local", "/api", "");
```

第一个参数与第二个参数和上述的方法一致，第三个参数是需要重写的路径，而第四个参数则是重写之后的内容，该方法就会将/api/user重写为/user，因此就可以正常访问了。

## WebSocket中转

在TurboWeb2.2.x版本中，支持对WebSocket进行透传，具体的配置规则与普通的http几乎一致，假设我们需要通过网关访问另一个WebSocket服务器，如下代码：

```java
public class WsApplication {
    public static void main(String[] args) {
        BootStrapTurboWebServer.create()
                .protocol()
                .websocket("/ws", new AbstractWebSocketHandler() {
                    @Override
                    public void onOpen(WebSocketSession session) {
                        session.sendText("管道建立成功");
                    }
                    @Override
                    public void onText(WebSocketSession session, String content) {
                    }
                    @Override
                    public void onBinary(WebSocketSession session, ByteBuf content) {
                    }
                })
                .and()
                .start(8081);
    }
}
```

这里WebSocket的服务器端口号是8081，下面我们创建一个网关端口是8080，本身不设置WebSocket:

```java
public class GatewayApplication {
    public static void main(String[] args) {
        GatewayChannelHandler<Boolean> gatewayHandler = GatewayChannelHandler.create();
        // 设置远程服务节点
        gatewayHandler.addService("wsService", "localhost:8081");
        // 添加映射规则
        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/ws/**", "ws://wsService");
        gatewayHandler.setRule(ruleManager);

        BootStrapTurboWebServer.create()
                .gatewayHandler(gatewayHandler)
                .start(8080);
    }
}
```

接下类我们创建websocket的客户端访问8080端口的服务器:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
</head>
<body>
<script>
    const ws = new WebSocket('ws://localhost:8080/ws');
    ws.onopen = function (event) {
        ws.send('hello');
    }
    ws.onmessage = function (event) {
        console.log(event.data);
        ws.send('hello')
    }
</script>
</body>
</html>
```

**说明**

WebSocket的中转规则配置与http的中转规则几乎一致，唯一的不同就是服务表达式中协议的不同，是需要配置ws协议。

需要注意的是TurboWeb在进行WebSocket中转的时候有首帧丢失的概率，因为TurboWeb实现WebSocket的中转需要建立双向的连接，即客户端-网关，网关-远程节点，因此当客户端建立连接之后网关还没有初始化和远程节点的连接，这个时候客户端发送的帧可能会丢失。

后续版本会解决这个问题。

## 网关过滤器

TurboWeb的网关支持网关过滤器，可以提前实现鉴权功能，从而实现多个服务的同一鉴权。

TurboWeb的过滤器支持同步风格和异步风格，选择同步风格和异步风格需要根据网关处理器决定。

如下代码：

```java
GatewayChannelHandler<Boolean> gatewayHandler = GatewayChannelHandler.create();
```

因为当前网关处理器的泛型是Boolean，因此这个是同步风格，所以过滤器也只能使用同步风格：

```java
public class GatewayFilterApplication {
    public static void main(String[] args) {
        GatewayChannelHandler<Boolean> handler = GatewayChannelHandler.create();

        handler.addFilter((request, responseHelper) -> {
            System.out.println("GatewayFilter");
            return true;
        });


        NodeRuleManager ruleManager = new NodeRuleManager();
        ruleManager.addRule("/**", "http://local");
        handler.setRule(ruleManager);

        BootStrapTurboWebServer.create()
                .http()
                .middleware(new Middleware() {
                    @Override
                    public Object invoke(HttpContext ctx) {
                        return "Hello World";
                    }
                })
                .and()
                .gatewayHandler(handler)
                .start();
    }
}
```

添加网关通过addFilter的方式进行添加，如果返回值是true，标识鉴权通过，允许向后执行，若返回false，那么直接拒绝，用户可以设置响应的内容，如果用户不设置，框架会响应默认的内容：

```java
handler.addFilter((request, responseHelper) -> {
    System.out.println("GatewayFilter");
    // 设置响应的内容
    responseHelper.writeHtml("鉴权失败");
    return false;
});
```

响应内容的设置可以使用第二个回调进行设置，也就是ResponseHelper对象，如果返回true的情况切记不要设置响应内容，因为会造成响应错乱。

如果需要使用异步过滤器，那么需要使用异步的网关处理器，需要通过下述方式创建：

```java
GatewayChannelHandler<Mono<Boolean>> handler = GatewayChannelHandler.createAsync();

handler.addFilter((request, responseHelper) -> {
    System.out.println("GatewayFilter");
    // 设置响应的内容
    responseHelper.writeHtml("鉴权失败");
    return Mono.just(false);
});
```

其余方式不变，只不过过滤器中返回的内容必须是一个Mono。

**如果在同步风格的过滤器中执行IO阻塞的代码会不会影响Netty的IO线程？**

有的开发者可能看见在过滤器中写了同步的代码，阻塞的调用一些功能，害怕会阻塞Netty的IO线程，实际上即使过滤器有阻塞，那么对Netty的IO线程毫无影响，可以看下面的源码：

```java
@Override
public void startFilter(FullHttpRequest request, ResponseHelper responseHelper, ChannelPromise promise) {
    if (filters.isEmpty()) {
        promise.setSuccess();
        return;
    }
    // 增加引用
    request.retain();
    // 执行所有的过滤器
    Mono.<Boolean>create(sink -> {
                Thread.ofVirtual().name("filter-execute-thread").start(() -> {
                    for (GatewayFilter<Boolean> filter : filters) {
                        Boolean toNext = filter.filter(request, responseHelper);
                        toNext = toNext != null && toNext;
                        if (!toNext) {
                            sink.error(new TurboGatewayException("filter return false, then cancel"));
                            break;
                        }
                    }
                    sink.success(true);
                });
            })
            // 减少引用
            .doFinally(signalType -> request.release())
            .subscribe(
                    ok -> promise.setSuccess(),
                    promise::setFailure
            );
}
```

虽然同步风格的代码写起来是同步阻塞的，但是TurboWeb在底层会自动结合虚拟线程将过滤器的执行封装为Reactor Stream，因此对于Netty的IO线程来说还是非阻塞的调用，因此即使使用同步风格的过滤器，也可以在过滤器中书写IO阻塞的代码调用，例如查询Redis等。

## 断路器

TurboWeb支持断路器，因为在大多数的微服务场景中，下游的业务出现故障很容易拖慢上游的服务，引发雪崩，因此TurboWeb在网关中引入了断路器，默认不开启，如果开启会根据下游的服务健康程度进行熔断和降级，在超时之后会通过半开的方式来逐渐恢复。

```java
DefaultBreaker breaker = new DefaultBreaker();
// 设置被判断为失败的状态码
breaker.setFailStatusCode(500);
// 设置时间窗口内熔断失败的阈值，该例子的意思是10s内，失败次数超过200，则触发熔断
breaker.setFailThreshold(200);
breaker.setFailWindowTTL(10000);
// 设置尝试恢复触发时机，当熔断之后超过5s,转化为半开尝试恢复
breaker.setRecoverTime(5000);
// 设置处于半开状态为10s，尝试恢复成功率超过80%，则恢复为正常状态
breaker.setRecoverWindowTTL(10000);
breaker.setRecoverPercent(0.8);

// 将断路器设置进入网关
GatewayChannelHandler<Boolean> gatewayChannelHandler = GatewayChannelHandler.create(breaker);
```

如果要设置超时时间需要从构造器中设置：

```java
DefaultBreaker breaker = new DefaultBreaker(5000);
```



## 思想简介

TurboWeb 的嵌入式网关基于协议调度器实现，完整请求处理流程如下：

1. 协议识别：首先判断是否为 WebSocket 升级请求，若是则跳过网关逻辑，进入 WS 处理流程；

2. 路由匹配：根据 URI 匹配是否需要转发请求，若命中转发规则，则使用异步 HTTP 客户端（基于 Reactor Netty）将请求透传到目标节点；

3. 请求调度：若未命中转发路径，则由本地 HttpScheduler 执行本地中间件链路及 Controller 调用逻辑。

## 推荐使用场景

| 场景                       | 是否推荐                                 |
| -------------------------- | ---------------------------------------- |
| 本地开发调试               | ✅ 强烈推荐                               |
| 服务数量较少（<10）        | ✅ 推荐                                   |
| 单体拆分后集成部署         | ✅ 推荐                                   |
| 高并发生产环境，服务规模大 | ⚠️ 谨慎使用（建议配合注册中心或独立网关） |
| WebSocket 统一接入         | ✅ 推荐(需要避免首帧丢失)                 |



[首页](../README.md) | [WebSocket](./websocket.md) | [监听器](./listener.md)