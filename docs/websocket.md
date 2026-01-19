# WebSocket

在现代高交互性的 Web 应用中，客户端与服务器之间的双向通信能力愈发重要。TurboWeb 原生支持 **WebSocket 协议**，为开发者提供了一套高性能、低延迟、易扩展的实时通信机制，广泛适用于**聊天室、在线游戏、数据监控、协同编辑**等典型实时场景。

WebSocket 是一种基于 TCP 的 **全双工通信协议**，相较于轮询（Polling）或服务器推送（SSE），它允许 **客户端与服务端互相主动发送消息**，在保持长连接的前提下极大降低了通信延迟与资源开销。

**_TurboWeb 中 WebSocket 的优势_**

TurboWeb 在底层采用 Netty 实现 WebSocket 协议支持，具备如下显著特点：

- ✅ **零依赖集成**：无需引入第三方库，开箱即用。
- ✅ **非阻塞高并发**：基于 Netty 的异步通信模型，支持大规模并发连接。
- ✅ **连接生命周期可控**：支持完整的连接事件回调（open、message、close、ping/pong）。
- ✅ **线程模型灵活可调**：支持虚拟线程与线程池模型切换，兼顾吞吐与延迟。
- ✅ **与 HTTP 路由共存**：WebSocket 与常规 HTTP 服务无缝集成，统一注册和管理。

## 快速上手

**_定义WebSocket处理器_**

TurboWeb 的 WebSocket 支持以 `WebSocketHandler` 接口为核心，开发者只需实现该接口，即可定义连接、消息、关闭等逻辑：

```java
public class MyWebSocketHandler implements WebSocketHandler {
    @Override
    public void onOpen(WebSocketSession session) {
        System.out.println("onOpen");
    }

    @Override
    public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
        // 收到消息
        try {
            if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
                String message = textWebSocketFrame.text();
                System.out.println("onMessage: " + message);
                // 向客户端推送文本消息
                session.sendText(message);
            }
        } finally {
            // 释放frame避免内存泄漏
            webSocketFrame.release();
        }
    }

    @Override
    public void onClose(WebSocketSession session) {
        System.out.println("onClose");
    }
}
```

**_注册WebSocket处理器_**

通过 `BootStrapTurboWebServer` 注册 WebSocket 路由和处理器：

```java
BootStrapTurboWebServer.create()
        .protocol()
    	// 注册websocket处理器
        .websocket("/ws", new MyWebSocketHandler())
        .and().start();
```

其中 `/ws` 是连接的访问路径。

**_测试_**

```javascript
const ws = new WebSocket('ws://localhost:8080/ws');
ws.onopen = function (event) {
    ws.send('hello');
}
ws.onmessage = function (event) {
    console.log(event.data);
    ws.close()
}
```

**_使用抽象处理器简化开发_**

由于直接实现 `WebSocketHandler` 需要手动识别帧类型并释放资源，TurboWeb 提供了一个更方便的抽象类 `AbstractWebSocketHandler`，帮助开发者简化逻辑处理：

```java
public class MyWebSocketHandler extends AbstractWebSocketHandler {

    @Override
    public void onText(WebSocketSession session, String content) {
        System.out.println("收到文本消息: " + content);
        // 向客户端推送文本消息
        session.sendText("收到消息: " + content);
    }

    @Override
    public void onBinary(WebSocketSession session, ByteBuf content) {
        System.out.println("收到二进制消息");
        // 向客户端推送二进制消息
        session.sendBinary(content);
    }
}
```

该抽象类会自动完成：

- Frame 类型分发（Text / Binary / Ping / Pong）
- 内存回收（无需显式调用 `release()`）
- 提供默认实现（只需重写需要的方法）

如果你需要监听更多连接事件，也可自由重写：

```java
@Override
public void onOpen(WebSocketSession session) { ... }

@Override
public void onClose(WebSocketSession session) { ... }

@Override
public void onPing(WebSocketSession session) { ... }

@Override
public void onPong(WebSocketSession session) { ... }
```

## API详解

**_获取当前连接的路径_**

```java
String path();
```

**_获取当前连接的请求头部信息_**

```java
HttpHeaders headers();
```

**_发送文本帧_**

```java
ChannelFuture sendText(String message);
```

**_发送二进制帧_**

```java
ChannelFuture sendBinary(byte[] message);
```

```java
ChannelFuture sendBinary(ByteBuf byteBuf);
```

**_发送自定义帧_**

```java
ChannelFuture send(WebSocketFrame webSocketFrame);
```

**_获取远程客户端的地址_**

```java
SocketAddress remoteAddress();
```

**_获取本地服务器的地址_**

```java
SocketAddress localAddress();
```

**_连接域对象存储_**

有的时候我们可能需要在某个WebSocket连接的周期中存储一些数据或者状态，在TurboWeb中提供了如下的API：

```java
/**
 * 向会话中存储一个自定义属性。
 *
 * @param key   属性键
 * @param value 属性值
 */
void putAttr(String key, Object value);

/**
 * 如果当前值与期望值相等，则更新会话属性。
 *
 * @param key      属性键
 * @param oldValue 期望的旧值
 * @param newValue 新值
 * @return 是否更新成功
 */
boolean compareAndPutAttr(String key, Object oldValue, Object newValue);

/**
 * 当属性不存在时存储新值，若属性已存在，则返回旧值且不修改。
 *
 * @param key   属性键
 * @param value 属性值
 * @return 若属性已存在返回旧值，否则返回 {@code null}
 */
Object putAttrIfAbsent(String key, Object value);

/**
 * 获取指定键的会话属性。
 *
 * @param key 属性键
 * @return 属性值，如果不存在则返回 {@code null}
 */
Object getAttr(String key);

/**
 * 获取指定键的会话属性，并转换为指定类型。
 *
 * @param key  属性键
 * @param type 属性值类型
 * @param <T>  属性值泛型
 * @return 属性值，如果不存在或类型不匹配返回 {@code null}
 */
<T> T getAttr(String key, Class<T> type);
```



## 高级特性

**_动态路径支持_**

你可以通过正则表达式注册 WebSocket 路径，以支持参数化连接：

```java
websocket("/ws/(.*)", new MyWebSocketHandler())
```

连接示例：

```java
const ws = new WebSocket('ws://localhost:8080/ws/123456');
```

在服务端，你可以通过 `WebSocketSession` 获取连接路径：

```java
@Override
public void onOpen(WebSocketSession session) {
    // 获取建立连接的路径
    String path = session.getWebSocketConnectInfo().getWebsocketPath();
    System.out.println("path: " + path);
}
```

**_灵活线程模型_**

默认情况下，TurboWeb 为每条 WebSocket 消息分配一个 **虚拟线程**，便于处理 I/O 密集任务。但在某些高性能场景下，你可能希望切换为线程池模型：

```java
websocket("/ws/(.*)", new MyWebSocketHandler(), 8)
```

调用带线程数参数的重载方法后：

- 虚拟线程将被禁用；
- 收到消息后会直接提交到 ForkJoinPool；
- 更适用于 CPU 密集型、低延迟任务处理。



[首页](../README.md) | [Server-Sent Events](./sse.md) | [嵌入式网关](./gateway.md)

