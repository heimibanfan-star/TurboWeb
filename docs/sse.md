# Server-Sent Events

在现代 Web 应用中，服务器主动向客户端推送实时消息已成为提升用户体验的重要手段。TurboWeb 原生支持 **SSE（Server-Sent Events）**，使开发者无需引入复杂协议，即可轻松实现基于 HTTP 的实时单向数据推送。

SSE 是一种基于 HTTP 协议的 **单向长连接机制**，允许服务器持续不断地向客户端推送事件。相比于 WebSocket，SSE 具有协议简单、实现轻量、对客户端友好等优势，尤其适用于日志输出、进度更新、实时通知等场景。

TurboWeb 的 SSE 支持底层基于 Netty 实现，**不依赖中间件且不受线程调度器限制**，充分利用框架的非阻塞特性，能够稳定支持高并发、大规模的实时推送连接。

在TurboWeb中SSE的使用主要有两种方式：

- SseResponse
- SseEmitter

## SseResponse

**_基本使用_**

通过 `HttpContext` 的 `createSseResponse()` 方法创建 `SseResponse` 实例，并通过回调定义推送逻辑：

```java
@Get("/sse1")
public SseResponse sse1(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();
    sseResponse.setSseCallback(session -> {
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                session.send("hello:" + i);
            }
            session.close();
        });
    });
    return sseResponse;
}
```

> **注意：**
>  不要将回调中的 `session` 对象提取到回调外部作用域使用，否则可能导致消息丢失。

**_接收Flux流_**

`SseResponse` 支持接收 Reactor 的 `Flux` 流，实现响应式推送：

```java
@Get("/sse2")
public SseResponse sse2(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();
    Flux<String> flux = Flux.just("hello1", "hello2", "hello3").delayElements(Duration.ofSeconds(1));
    sseResponse.setSseCallback(flux);
    return sseResponse;
}
```

**_异常处理与结束回调_**

支持捕获 `Flux` 流中的异常，发送错误消息，并执行结束回调：

```java
@Get("/sse3")
public SseResponse sse3(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();
    // 创建一个Flux流，抛出异常
    Flux<Integer> flux = Flux.just(1, 2, 3)
            .delayElements(Duration.ofSeconds(1))
            .flatMap(i -> {
                if (i == 3) {
                    return Mono.error(new RuntimeException("error"));
                }
                return Mono.just(i);
            });
    sseResponse.setSseCallback(flux, err -> "errMsg:" + err.getMessage(), ConnectSession::close);
    return sseResponse;
}
```

## SseEmitter

当需要将 SSE 连接会话存储、共享，或跨线程/跨组件发送事件时，`SseEmitter` 提供了更灵活的方案。

**_设计原理_**

`SseEmitter` 通过内部缓冲区机制解决了初始化时间窗口导致的数据丢失问题：

- 创建时分配缓冲区，缓存发送的消息。
- 当连接初始化完成后，TurboWeb 进行管道重构，将缓冲区内容发送至客户端，销毁缓冲区。
- 后续发送操作直接写入连接，不再缓存。

**_基本使用_**

```java
@Get("/sse4")
public SseEmitter sse4(HttpContext context) {
    // sseEmitter可存储起来共享使用
    SseEmitter sseEmitter = context.createSseEmitter();
    // 发送数据
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            sseEmitter.send("hello:" + i);
        }
    });
    return sseEmitter;
}
```

可指定缓冲区大小（默认 32 条）：

```java
SseEmitter sseEmitter = context.createSseEmitter(32);
```

**_连接关闭监听_**

支持注册关闭事件回调，便于资源清理或业务处理：

```java
@Get("/sse5")
public SseEmitter sse5(HttpContext context) {
    SseEmitter sseEmitter = context.createSseEmitter();
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            sseEmitter.send("hello:" + i);
        }
        sseEmitter.close();
    });
    sseEmitter.onClose(emitter -> {
        System.out.println("close:" + emitter);
    });
    return sseEmitter;
}
```

**_注意事项_**

避免在返回 `SseEmitter` 之前执行耗时操作，否则会导致初始化时间窗口过长，缓冲区容易溢出从而造成数据丢失。

错误示范：

```java
@Get("/sse6")
public SseEmitter sse6(HttpContext context) throws InterruptedException {
    SseEmitter sseEmitter = context.createSseEmitter();
    TimeUnit.SECONDS.sleep(5); // 不推荐
    return sseEmitter;
}
```

推荐做法：

```java
@Get("/sse6")
public SseEmitter sse6(HttpContext context) throws InterruptedException {
    TimeUnit.SECONDS.sleep(5); // 耗时操作提前
    SseEmitter sseEmitter = context.createSseEmitter();
    return sseEmitter;
}
```

## SseResponse 与 SseEmitter 使用场景比较

| 特性/场景            | SseResponse                                                  | SseEmitter                                                   |
| -------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **适用场景**         | - 适合一次请求响应周期内，完成短时、连续推送任务- 适合“打字机”效果，逐字或逐句推送文本数据，模拟实时输出- 适合基于响应式流（Flux）构建的推送逻辑 | - 适合保持长连接的实时推送场景- 适合跨多个业务组件、线程共享和动态推送消息- 适合需要会话管理、连接生命周期控制的复杂场景 |
| **连接生命周期**     | 连接周期与请求相同，请求结束即关闭                           | 连接可长期存在，支持主动关闭和连接断开监听                   |
| **消息发送时机**     | 发送操作集中在回调内，响应建立后自动触发                     | 消息发送与响应建立解耦，支持任意时刻异步发送                 |
| **适合业务示例**     | - 服务器逐字返回生成的文字内容- 进度条或步骤状态按时间间隔推送- 短时会话的通知推送 | - 实时聊天消息推送- 股票行情、竞价报价实时推送- 服务器事件广播，推送系统状态变更等 |
| **资源管理和复杂度** | 轻量简单，适合快速开发和短流程                               | 功能更丰富，需管理缓冲区、连接状态和多线程并发               |
| **响应式支持**       | 原生支持 Reactor Flux，异常和结束状态易管理                  | 不直接支持 Flux，需自行实现事件驱动或异步机制                |

**_典型示例补充说明_**

**SseResponse — 模拟“打字机”效果**

服务器可通过循环定时推送一段文字的每个字符或词组，实现类似打字机的动态文本输出，适用于消息播报、实时日志输出、对话式交互等短时推送。

**SseEmitter — 长连接实时推送**

适用于实时性要求高的长连接推送场景，比如股票行情推送、多人聊天、在线协作系统等，允许不同业务模块异步发送消息给客户端，且支持连接状态监控和管理。



[首页](../README.md) | [Session](./session.md) | [WebSocket](./websocket.md)
