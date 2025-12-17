# Server-Sent Events

在现代 Web 应用中，**服务器向客户端主动推送实时消息**已成为提升交互体验的重要能力。
 TurboWeb 原生支持 **Server-Sent Events（SSE）**，开发者无需引入复杂的通信协议，即可基于标准 HTTP 实现高效、稳定的单向实时数据推送。

SSE 是一种基于 HTTP 的 **单向长连接机制**，允许服务器持续向客户端发送事件流。
 相较于 WebSocket，SSE 具有以下优势：

- 协议简单，基于标准 HTTP
- 浏览器原生支持，客户端实现成本低
- 天然支持断线重连（`Last-Event-ID`）
- 非常适合日志流、进度通知、实时输出等场景

TurboWeb 的 SSE 能力底层基于 **Netty** 实现，**不依赖外部中间件，也不受线程调度模型限制**，充分发挥框架的非阻塞特性，可稳定支撑**高并发、长连接、持续推送**的业务场景。

**_SSE 使用方式概览_**

TurboWeb 提供两种 SSE 使用模型，分别适配不同的开发风格与业务需求：

- **`SseResponse`**：偏底层、响应驱动式，适合响应式流或精细控制推送逻辑
- **`SseEmitter`**：偏命令式、线程安全，适合跨线程、非响应式推送场景

## SseResponse

`SseResponse` 是一种 **基于回调模型** 的 SSE 响应方式，适合在 **HTTP 请求生命周期内** 精确控制 SSE 的初始化、订阅与推送逻辑。

它强调：

- SSE 与请求强绑定
- 生命周期清晰
- 不鼓励跨线程、跨上下文使用

### 基本使用

**_代码示例_**

```java
@Get("/sse1")
public SseResponse sse1(HttpContext context) {

    SseResponse sseResponse = context.createSseResponse();
    sseResponse.setSseCallback(session -> {
        Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 10; i++) {
                session.send("data:" + i + "\n\n");
            }
            session.close();
        });
    });
    return sseResponse;
}
```

通过 `HttpContext#createSseResponse()` 创建 `SseResponse`

使用 `setSseCallback(...)` 注册 SSE 初始化完成后的回调

回调参数 `ConnectSession` 用于向客户端发送数据

SSE 消息必须符合标准格式（字段 + 换行结束符 `\n\n`）

数据推送完成后需主动调用 `session.close()` 关闭连接

> 注意：
>
> `ConnectSession` **仅允许在回调作用域内使用**，切勿保存到成员变量或外部上下文中，否则可能引发不可预期的并发与生命周期问题。

**_为什么通过SseResponse就可以实现SSE的推送？_**

`SseResponse` 实现了 `InternalCallResponse` 接口，该接口用于标识：

> **该响应由 TurboWeb 内部调度器接管执行**

当调度器检测到返回值类型为 `SseResponse` 时，会执行以下流程：

1. 写入 SSE 必要的 HTTP 响应头（`Content-Type: text/event-stream` 等）
2. 切换连接为长连接模式
3. 调用内部 `startSse()` 方法来执行回调。

### 对Reactor Flux的原生支持

`SseResponse` 原生支持 **Reactor `Flux`**，适用于响应式、流式数据推送场景。

**_代码示例_**

```java
@Get("/sse2")
public SseResponse sse2(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();

    // 创建一个Flux流
    Flux<String> flux = Flux.just("hello1", "hello2", "hello3")
            .map(item -> "data:" + item + "\n\n");

    sseResponse.setSseCallback(flux);
    return sseResponse;
}
```

TurboWeb 会在 SSE 初始化完成后自动订阅该 `Flux` 并将数据逐条推送给客户端。

**_更加详细的调用_**

TurboWeb针对Flux流的支持还提供了一个重载方法，如下使用：

```java
@Get("/sse3")
public SseResponse sse3(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();
    // 创建一个Flux流
    Flux<String> flux = Flux.just(1, 2, 3)
            .doOnNext(item -> {
                if (item == 2) throw new RuntimeException("测试异常");
            })
            .map(item -> "data:" + item + "\n\n");

    sseResponse.setSseCallback(flux, err -> "error:" + err.getMessage(), ConnectSession::close);
    return sseResponse;
}
```

第一个参数：数据流

第二个参数：异常发生时的推送内容（可选）

第三个参数：流完成后的回调（默认关闭连接）

**_每次都要拼接SSE的格式会不会太繁琐了？_**

有的时候我们可能传入一个Flux流，例如大模型的一个流式响应，而我们不需要特殊的格式的字段，直接data即可，这个时候可以使用TurboWeb提供的简化方法 `setFlux(..)`

```java
@Get("/sse4")
public SseResponse sse4(HttpContext context) {
    SseResponse sseResponse = context.createSseResponse();
    sseResponse.setFlux(Flux.just("Hello", "World"));
    return sseResponse;
}
```

该方法TurboWeb会自动拼接data和换行符。

## SseEmitter

`SseEmitter` 是一种 **线程安全、命令式** 的 SSE 推送模型，适合以下场景：

- 跨线程发送 SSE 消息
- 非响应式编程模型
- 连接生命周期明显长于请求生命周期

**_代码示例_**

```java
@Get("/sse5")
public SseEmitter sse5(HttpContext context) {
    // 创建sse发射器
    SseEmitter sseEmitter = context.createSseEmitter();
    // 创建线程发送信息
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            sseEmitter.sendData(i + "");
        }
        sseEmitter.close();
    });
    return sseEmitter;
}
```

创建 `SseEmitter`

方法返回后，TurboWeb 完成 SSE 初始化

任意线程可安全调用 `sendData(...)`

推送完成后调用 `close()`

**_SseEmitter是如何实现发送SSE的？_**

`SseEmitter` 同样实现了 `InternalCallResponse`，但其核心目标是：

> **解决 SSE 初始化前后消息发送的时间窗口问题**

**_什么是时间窗口问题？_**

- SSE 尚未初始化（响应头未发送）
- 业务线程已经开始发送消息
- 可能导致 HTTP 协议破坏或客户端无法解析

**_SseEmitter 的解决方案_**

`SseEmitter` 内部采用 **双管道模型**：

初始化前：

- 所有消息写入内存缓冲管道

初始化阶段：

- 阻塞所有发送线程
- 发送 HTTP 响应头并建立 SSE 通道
- 将缓冲消息依次刷出

初始化完成后：

- 切换为直写连接管道
- 放行所有发送线程

该设计确保：

- 消息绝不丢失
- 顺序严格一致
- 对并发发送完全安全

**_消息类型有哪些？_**

`SseEmitter` 支持常见的消息类型格式，也可以自定义消息格式，方法如下:

```java
// "data:" + data + "\n\n"
public void sendData(String data){
    ...
}

// "event:" + event + "\n\n"
public void sendEvent(String event){
    ...
}

// "id:" + id + "\n\n"
public void sendId(String id){
    ...
}

// "retry:" + retry + "\n\n"
public void sendRetry(int retry){
    ...
}

// ":" + comment + "\n\n"
public void sendComment(String comment){
    ...
}
```

如果不想让TurboWeb自动拼接消息可以直接调用`send(..)` 方法。

**_监听SseEmitter的关闭_**

有的时候我们需要监听 `SseEmitter` 的关闭来进行一些清理操作，参考代码如下:

```java
@Get("/sse6")
public SseEmitter sse6(HttpContext context) {
    SseEmitter sseEmitter = context.createSseEmitter();
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            sseEmitter.sendData(i + "");
        }
        sseEmitter.close();
    });

    // 监听sse的关闭
    sseEmitter.onClose(emitter -> {
        System.out.println("SSE已关闭:" + emitter);
    });

    return sseEmitter;
}
```

**_SseEmitter的配置_**

由于SseEmitter解决了时间窗口消息发送的问题，因此也有一个缓存容量，在创建SseEmitter的时候可以指定:

```java
/**
 * 创建指定缓存大小的 SSE 事件发射器。
 *
 * @param maxMessageCache SSE 消息缓存的最大容量
 * @return {@link SseEmitter} 对象，用于服务端事件推送。
 */
SseEmitter createSseEmitter(int maxMessageCache);
```

默认情况下缓存32条信息。



[首页](../README.md) | [Session](./session.md) | [WebSocket](./websocket.md)
