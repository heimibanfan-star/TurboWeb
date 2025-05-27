# <img src="../image/logo.png"/>

# SSE的支持

Server-Sent Events（SSE）是一种基于 HTTP 协议的服务端推送技术。通过建立一个单向连接，服务端可以持续不断地将文本事件数据推送给客户端，而无需客户端主动轮询。

SSE 使用标准的 `text/event-stream` 格式进行通信，连接由客户端通过普通的 HTTP 请求发起，服务端在响应中保持连接不断开，并按需推送事件数据。该机制具备传输稳定、兼容性良好、实现简单等优势，特别适合构建轻量级的实时推送系统。

TurboWeb 原生支持 SSE，底层基于 Netty 连接通道，结合事件驱动模型，无需引入额外依赖即可高效实现服务端推送。通过注册 SSE 路由，服务端可在连接生命周期内随时向指定客户端发送事件。

SSE 是在浏览器环境中建立服务端推送连接的标准方案，适用于在线状态通知、进度更新、监控推送、轻量级 IM 等实时性场景。

TurboWeb中的SSE有三种类型，分别是SSE发射器、SSE回调函数、流式SSE回调(反应式风格)。

## SSE发射器

故名思意，SSE发射器是通过向一个对象中发送信息来完成数据的推送，同样这也是TurboWeb在同步编程中最推荐使用的一种SSE推送方式。

接下来看一下如何使用它：

```java
@Get("/example01")
public HttpResponse example01(HttpContext c) {
    SseEmitter sseEmitter = c.newSseEmitter();
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            sseEmitter.send("hello:" + i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        sseEmitter.close();
    });
    return sseEmitter;
}
```

通过 `HttpContext` 的 `newSseEmitter()` 方法来获取当前连接通道的SSE发射器。

之后创建一个虚拟线程异步对这个SSE发射器发送10条信息，发送完之后关闭SSE发射器。

SSE发射器一旦关闭就不能继续发送消息了。

**那么这段代码的原理是什么呢？**

`SseEmitter` 也是一个 `HttpResponse` 类型类型的对象，当TurboWeb发现如果对象类型是 `SseEmitter` 那么就会认为这个操作是SSE推送，因此只返回一个SSE响应头，后续的内容交给SSE发射器来推送。

`SseEmitter` 是可以线程之间共享的，它是**线程安全**的对象。

## SSE回调函数

实现SSE的第二种方式就是通过回调函数来实现，如下面的例子：

```java
@Get("/example02")
public HttpResponse example02(HttpContext c) {
    SseResponse sseResponse = c.newSseResponse();
    sseResponse.setSseCallback((session) -> {
        for (int i = 0; i < 10; i++) {
            session.send("hello:" + i);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        session.close();
    });
    return sseResponse;
}
```

在该示例中，首先通过 `HttpContext` 的 `newSseResponse()` 方法获取一个 `SseResponse` 实例。然后，通过调用 `setSseCallback(...)` 方法设置 SSE 的业务回调逻辑。在回调函数中，可以使用 `ConnectSession` 来向客户端发送消息。

> 注意：
>
> 尽管 `ConnectSession` 对象可以在回调中用于发送消息，但**不应将其引用传递到回调作用域之外**，否则可能因为SSE响应头未发送从而导致数据丢失。正确的做法是仅在回调函数内部使用 `session` 实例。

## 为什么SSE发射器可以全局共享？

这需要从 SSE 发射器的工作机制和回调函数的触发时机说起。

SSE 的核心原理在于：**只有在响应头被成功发送之后，客户端才能接收后续推送的内容**。如果在响应头尚未发送的阶段尝试推送消息，这些内容将被丢弃。

**先来看一下SSE回调函数**

在使用回调方式实现 SSE 时，`setSseCallback(...)` 方法会注册一个回调函数，此时回调函数本身并不会立即执行。真正的触发时机是在 `SseResponse` 返回并由 TurboWeb 的 HTTP 调度器处理时。调度器会**先推送 SSE 响应头**，确保客户端完成协议建立，然后才调用开发者注册的回调函数。因此，在标准使用流程中，消息发送不会出现丢失。

但问题出现在当我们将 `ConnectSession` 的引用提升到回调函数作用域之外时。例如，另一个线程在回调尚未触发前就通过该 `ConnectSession` 尝试发送消息，这时由于响应头尚未发送，SSE 通道尚未建立，**所有发送的消息都会丢失**。

这实际上是一个**时序窗口问题**。为了避免这种情况，建议始终在回调函数内部使用 `ConnectSession`，并确保所有推送逻辑发生在 SSE 通道建立之后。

**那么SSE发射器又如何解决时序窗口问题的呢？**

答案在于其内部设计的**消息缓冲区**机制。

SSE 发射器在响应头尚未发送前，所有调用其发送消息的方法，都会将消息暂存到内部缓冲区中。而当 SSE 发射器对象被传递到 HTTP 调度器时，会发生一次“管道重构”过程：

调度器首先会尝试抢占 SSE 发射器的写锁。抢占成功后，所有尝试向发射器发送消息的线程将被阻塞，进入等待状态。调度器随后执行以下步骤：

1. 推送 SSE 响应头；
2. 清空消息缓冲区，将其内容写入连接通道；
3. 替换内部缓冲机制，将后续发送操作直接绑定到连接通道；
4. 释放写锁，允许阻塞线程恢复执行。

这个过程确保了无论消息何时发送，最终都能安全传达给客户端，从而彻底规避了时序窗口带来的消息丢失风险。

**SSE发射器一定不会出现消息丢失吗？**

从设计上来看，只要消息缓冲区容量合理，SSE 发射器几乎不会发生消息丢失。因为在正常使用中，SSE 发射器创建之后很快就会被调度器接管，其到达 HTTP 调度器的时间窗口非常短。大多数情况下，消息的发送操作都发生在**管道重构**之后，直接写入连接通道。

即便在管道重构之前提前发送了部分消息，这些消息也会被暂存在发射器的缓冲区中。默认情况下，TurboWeb 为每个发射器分配一个最多缓存 32 条消息的缓冲区，可满足大多数场景。如果提前发送的消息数量超过缓冲区容量，才有可能出现消息丢失。因此，只要合理设置缓冲区大小，几乎可以完全避免丢失问题。

可以通过以下方式自定义缓冲区容量：

```java
SseEmitter sseEmitter = c.newSseEmitter(128);
```

该方法创建一个带有指定缓冲区大小的 SSE 发射器。根据应用实际情况调整该值，可进一步增强消息可靠性。



[目录](./guide.md) [Session](./session.md) 上一节 下一节 [WebSocket的支持]()