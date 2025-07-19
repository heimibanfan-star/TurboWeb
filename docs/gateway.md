# 嵌入式网关

你的总结非常准确，TurboWeb 的嵌入式网关机制**适用于服务较少、网络拓扑简单的场景**，但在大规模微服务系统中确实可能存在维护复杂度高、路由同步不易等问题。下面是对你这段内容的专业化润色版本，提升整体表达的严谨性、逻辑性与技术深度，同时补充对架构优势与边界的说明。

**_核心优势_**

✅ 原生集成，开箱即用：网关能力作为 TurboWeb 协议调度器的一部分，直接集成在框架内部，零配置启动，开发者只需定义路由映射即可使用。

✅ SSE 转发支持：除常规 HTTP 请求外，还原生支持 SSE（Server-Sent Events） 的透传与多节点流式分发。

✅ 极低资源占用：基于异步 IO 与事件驱动模型，性能开销远低于传统反向代理或独立网关进程。

✅ 无中心架构，天然容灾：每个服务节点都具备独立路由能力，可有效规避中心化网关的单点故障问题，提升系统的容错性与可用性。

**_当前限制_**

❌ 暂不支持 WebSocket 协议转发：由于 WebSocket 协议需要握手升级并保持长连接，目前版本不支持在嵌入式网关中转发 WebSocket 连接。

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
@RequestPath("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "Hello User";
    }
}

@RequestPath("/order")
public class OrderController {
    @Get
    public String order(HttpContext context) {
        return "Hello Order";
    }
}
```

**_创建启动类并配置网关_**

用户服务节点：

```java
public class UserApplication {
    public static void main(String[] args) {
        // 配置网关
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://localhost:8081");

        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                // 注册网关
                .protocol().gateway(gateway)
                .and()
                .http().routerManager(routerManager)
                .and().start();
    }
}
```

订单服务节点：

```java
public class OrderApplication {
    public static void main(String[] args) {
        // 配置网关
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/user", "http://localhost:8080");

        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new OrderController());

        BootStrapTurboWebServer.create()
                .protocol().gateway(gateway)
                .and()
                .http().routerManager(routerManager)
                .and().start(8081);
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

```http
GET http://localhost:8081/user
```

```http
GET http://localhost:8081/order
```

可以发现，访问任何一个节点都可以同时访问两个模块所有的功能。

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
| WebSocket 统一接入         | ❌ 暂不支持                               |

未来版本可进一步支持自动路由注册、WebSocket 转发、健康检查等功能，增强分布式部署能力。



[首页](../README.md) | [WebSocket](./websocket.md) | [监听器](./listener.md)