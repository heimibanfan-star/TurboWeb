# WebSocket

TurboWeb 的 WebSocket 支持由独立的 `WebSocketServerRuntime` 提供，与 HTTP 应用共用同一台服务器与同一条连接管道：普通 HTTP 请求交给 HTTP 应用处理，命中 WebSocket 路径的升级请求由 WebSocket 应用接管，其余请求继续沿管道向后传递。

业务侧只与两个契约打交道：

- `WebSocketHandler`：把入站帧解码成文本或字节数组后回调，适合绝大多数场景。
- `WebSocketSession`：表示一条已建立的连接，提供发送与连接信息访问。

## 变更说明

- WebSocket 重新接入服务器运行时体系，注册入口是 `BootStrapWebServer#registerRuntimeLast(ServerRuntime)`；旧的 `...websocket(...)` 注册方式与 `WebSocketApplication` 类均已不存在。
- 装配类为 `WebSocketServerRuntime`，建造者方法为 `path(...)` 与 `handler(...)`。
- 包名由 `top.turboweb.websocket.handler` 平铺到 `top.turboweb.websocket`。
- 处理器基类由 `AbstractWebSocketHandler` 更名为 `WebSocketHandler`；需要 Netty 原始帧时实现 `UnSafeWebSocketHandler`。
- 会话不再提供属性挂载入口，连接级业务状态由使用方自行维护。
- WebSocket 异常收敛为模块内的 `WebSocketException`。

## 1. 引入依赖

WebSocket 应用与 HTTP 应用是两个独立模块，同时提供 HTTP 与 WebSocket 服务时两个都引入：

Gradle（Kotlin DSL）：

```kotlin
dependencies {
    implementation("io.gitee.turboweb:turboweb-http-server:$turbowebVersion")
    implementation("io.gitee.turboweb:turboweb-websocket:$turbowebVersion")
}
```

Maven：

```xml
<dependency>
    <groupId>io.gitee.turboweb</groupId>
    <artifactId>turboweb-http-server</artifactId>
    <version>${turboweb.version}</version>
</dependency>
<dependency>
    <groupId>io.gitee.turboweb</groupId>
    <artifactId>turboweb-websocket</artifactId>
    <version>${turboweb.version}</version>
</dependency>
```

`turboweb-websocket` 只依赖服务器运行时契约、微任务执行器与 Netty，不传递 `turboweb-http-server`；`BootStrapWebServer` 仍由 `turboweb-http-server` 提供。

## 2. 编写处理器

继承 `WebSocketHandler`，实现 `onText` 与 `onBinary`。`onOpen`、`onClose`、`onPing`、`onPong` 都有默认实现，按需覆盖：

```java
package demo;

import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

public class EchoHandler extends WebSocketHandler {

    @Override
    public void onOpen(WebSocketSession session) {
        System.out.println("connected: " + session.path());
    }

    @Override
    public void onText(WebSocketSession session, String content) {
        session.sendText("echo: " + content);
    }

    @Override
    public void onBinary(WebSocketSession session, byte[] content) {
        session.sendBinary(content);
    }

    @Override
    public void onClose(WebSocketSession session) {
        if (session != null) {
            System.out.println("closed: " + session.remoteAddress());
        }
    }
}
```

各回调的语义：

| 回调 | 说明 |
| --- | --- |
| `onOpen(WebSocketSession)` | 握手完成、会话创建后调用，早于该连接的任何消息 |
| `onText(session, String)` | 收到文本帧，参数是解码后的字符串 |
| `onBinary(session, byte[])` | 收到二进制帧，参数是帧内容的拷贝 |
| `onPing(session, byte[])` | 收到 Ping 帧，默认回送一个空载荷 Pong |
| `onPong(session, byte[])` | 收到 Pong 帧，默认不做处理 |
| `onClose(WebSocketSession)` | 连接断开时调用；握手未完成就断开时参数可能为 `null` |

`onText` 与 `onBinary` 是抽象方法，必须实现；其余方法按需覆盖。覆盖 `onPing` 后默认的自动回 Pong 不再发生，需要自己调用 `session.sendPong()`。

处理器实例在整个运行时内只有一份，被所有连接共用，回调可能在不同线程上并发执行，因此不要在实例字段里保存单条连接的临时数据，共享状态要用线程安全的容器。

## 3. 装配与启动

WebSocket 应用由 `WebSocketServerRuntime.Builder` 装配：`path(...)` 声明升级请求的路径表达式，`handler(...)` 指定业务处理器，两者都必填。

```java
package demo;

import top.turboweb.http.router.RestRouteRegister;
import top.turboweb.http.server.BootStrapWebServer;
import top.turboweb.http.server.runtime.HttpServerRuntime;
import top.turboweb.websocket.WebSocketServerRuntime;

public class Main {

    public static void main(String[] args) {
        // 1. HTTP 应用
        RestRouteRegister routeRegister = new RestRouteRegister();
        routeRegister.registerRoute(new HelloController());
        HttpServerRuntime httpRuntime = new HttpServerRuntime.Builder()
                .routeRegister(routeRegister)
                .build();

        // 2. WebSocket 应用
        WebSocketServerRuntime webSocketRuntime = new WebSocketServerRuntime.Builder()
                .path("/ws/.*")
                .handler(new EchoHandler())
                .build();

        // 3. 注册并启动：HTTP 在前，WebSocket 在后
        BootStrapWebServer server = new BootStrapWebServer()
                .registerRuntimeFirst(httpRuntime)
                .registerRuntimeLast(webSocketRuntime);
        server.start(8080);
    }
}
```

两个注册位置的差别只在管道中的装配顺序。HTTP 调度处理器遇到带 `Upgrade` 头的请求会原样向后转发，WebSocket 初始化处理器再接住它完成协议升级，因此 WebSocket 应用要排在 HTTP 应用之后。

注册必须发生在 `start` 之前，启动后再注册会抛 `IllegalStateException`。

启动后可以用浏览器控制台或 `websocat` 验证：

```js
const ws = new WebSocket("ws://localhost:8080/ws/chat");
ws.onopen = () => ws.send("hello");
ws.onmessage = (event) => console.log(event.data); // echo: hello
```

```bash
websocat ws://localhost:8080/ws/chat
# 输入 hello，收到 echo: hello
```

## 4. 会话 API

`WebSocketSession` 封装连接并提供发送能力，所有发送方法都是异步的：它们只把帧写入 Netty 管道，网络错误通过返回的 `ChannelFuture` 反映，不会在方法调用处抛出。

| 方法 | 说明 |
| --- | --- |
| `sendText(String)` | 发送文本帧，返回 `ChannelFuture` |
| `sendBinary(byte[])` | 发送二进制帧，返回 `ChannelFuture` |
| `sendBinary(ByteBuf)` | 以 `ByteBuf` 发送二进制帧，返回 `ChannelFuture` |
| `send(WebSocketFrame)` | 发送任意类型的帧，返回 `ChannelFuture` |
| `sendPing()` | 发送空载荷 Ping 帧 |
| `sendPong()` | 发送空载荷 Pong 帧 |
| `close()` | 关闭当前连接 |
| `path()` | 握手请求的 uri |
| `headers()` | 握手请求的请求头 |
| `remoteAddress()` | 客户端地址 |
| `localAddress()` | 服务端本地地址 |

需要确认发送结果时监听返回的 `ChannelFuture`：

```java
session.sendText("hello").addListener(future -> {
    if (!future.isSuccess()) {
        System.out.println("send failed: " + future.cause().getMessage());
    }
});
```

会话不提供属性挂载入口。需要按连接保存业务状态时，由使用方自己维护映射，并在 `onClose` 中清理：

```java
private final Map<WebSocketSession, String> names = new ConcurrentHashMap<>();

@Override
public void onText(WebSocketSession session, String content) {
    names.put(session, content);
}

@Override
public void onClose(WebSocketSession session) {
    if (session != null) {
        names.remove(session);
    }
}
```

`WebSocketSession` 没有重写 `equals` / `hashCode`，作为 Map 键时按对象身份区分连接。

## 5. 路径匹配与协议升级

`path(...)` 是正则表达式，按 `String.matches` 的整串匹配语义作用于完整的请求 uri：

```java
.path("/ws/.*")   // 匹配 /ws/chat、/ws/1；不匹配 /ws
.path("/ws")      // 只匹配 /ws
```

因为要求整串匹配，`/ws` 不会自动匹配 `/ws/chat`，需要子路径时显式写 `.*`。正则的元字符要按正则规则处理，例如想匹配字面量点号要写成 `\\.`。

只有同时满足以下条件的请求才会触发升级：

- 请求方法是 `GET`；
- `Upgrade` 头的值为 `websocket`（忽略大小写）；
- `Connection` 头包含 `upgrade`（忽略大小写，兼容 `keep-alive, Upgrade` 这类多值写法）；
- uri 与路径表达式整串匹配。

其余请求（普通 HTTP 请求、其他升级协议、路径不匹配的 WebSocket 升级请求）不由本应用处理，会继续沿管道向后传递。普通 HTTP 请求由前面的 HTTP 应用消费；路径不匹配的升级请求如果后面没有别的处理器，就没有应用应答它。

## 6. 消息线程模型

入站帧不直接在 Netty 的 IO 线程上执行业务回调，而是提交给微任务执行器异步执行。默认使用非受限虚拟线程执行器：每条消息进入一个新虚拟线程，不做并发限制，也不会因资源不足而拒收。

需要限制消息处理并发时，可以在建造者上改用工作窃取执行器：

```java
WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
        .path("/ws/.*")
        .handler(new EchoHandler())
        .useForkJoin()      // 线程数取默认并行度
        // .useForkJoin(8)  // 或显式指定线程数，必须大于 0
        .build();
```

工作窃取模式使用本应用私有的隔离任务池，`WebSocketServerRuntime#close()` 时回收；默认的虚拟线程模式不持有可回收资源。

两个线程相关的事实需要记住：

- **同一连接的消息回调不保证串行，也不保证按到达顺序执行。** 不同消息可能同时运行在不同线程上；业务要求同一会话内严格有序时，需要在处理器里自行加锁。
- **`onOpen` 与 `onClose` 在 Netty IO 线程上回调。** 这两个回调里不要做阻塞操作，耗时逻辑应另行提交出去。

`WebSocketInitChannelHandler.Builder#microTaskExecutor(...)` 还允许直接注入自定义 `MicroTaskExecutor`，属于底层用法：执行器的生命周期由注入方管理；执行器拒绝受理时，框架会释放该帧并记录一条丢弃日志，拒绝异常照常沿管道抛出。

## 7. 生命周期与关闭

一条连接的完整回调顺序是：

```text
onOpen -> onText / onBinary / onPing / onPong ... -> onClose
```

- `onOpen` 在握手完成、会话创建之后调用。
- 消息回调在后续任意时刻到达。
- `onClose` 在底层 channel 进入非活动状态时调用，覆盖客户端主动关闭、网络断开、调用 `session.close()` 以及服务器关闭这几种情况。仅当握手尚未完成连接就断开时，`onClose` 的参数为 `null`，实现中需要判空。

应用级资源在运行时关闭时释放：

```java
WebSocketServerRuntime runtime = new WebSocketServerRuntime.Builder()
        .path("/ws/.*")
        .handler(new EchoHandler())
        .useForkJoin()
        .build();

// 单独关闭
runtime.close();

// 或者随服务器一起关闭：BootStrapWebServer#close() 会按注册顺序关闭全部运行时
```

`close()` 可以重复调用。工作窃取模式下它会关闭本应用私有的任务池；使用默认虚拟线程模式时没有需要回收的线程资源。

## 8. 裸帧处理器

`WebSocketHandler` 会先把帧内容复制出来再回调，文本与字节数组在回调返回后仍然可用，代价是每条消息多一次内容拷贝。要省掉这次拷贝、直接在 Netty 原始帧上操作时，可以实现 `UnSafeWebSocketHandler`：

```java
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import top.turboweb.websocket.UnSafeWebSocketHandler;
import top.turboweb.websocket.WebSocketSession;

public class RawHandler implements UnSafeWebSocketHandler {

    @Override
    public void onOpen(WebSocketSession session) {
    }

    @Override
    public void onUnSafeText(WebSocketSession session, TextWebSocketFrame frame) {
        session.sendText(frame.text());
    }

    @Override
    public void onUnSafeBinary(WebSocketSession session, BinaryWebSocketFrame frame) {
    }

    @Override
    public void onUnSafePing(WebSocketSession session, PingWebSocketFrame frame) {
        session.sendPong();
    }

    @Override
    public void onUnSafePong(WebSocketSession session, PongWebSocketFrame frame) {
    }

    @Override
    public void onClose(WebSocketSession session) {
    }
}
```

裸帧接口不替使用方保管内存，**框架不会复制帧内容**，回调拿到的是 Netty 的原始帧对象：

- **引用计数由使用方自己掌管。** 需要把帧或其内容留到回调之外时，必须先 `retain()`，用完后 `release()`。忘记释放会造成内存泄漏；提前释放或重复释放会让引用计数下溢，随后读到的是已释放的内存。
- 回调执行期间框架会持有自己的引用，并在回调返回后归还该引用，因此**不要在回调内额外 `release()`**；也不要把没有 `retain()` 过的帧对象保存到回调之外。
- 帧与帧内容只在回调执行期间有效。只想留下内容时，复制成自己的数据（例如 `ByteBufUtil.getBytes(frame.content())`）比在帧上延长生命周期更安全。
- 裸帧接口把引用计数与内存安全的责任交给使用方，建议只在确有极致性能要求时使用；普通业务优先用 `WebSocketHandler`，由框架完成内容复制。

`WebSocketHandler` 已经实现了这四个 `onUnSafe*` 方法并标记为 `final`，继承它之后无法绕过内容复制。

## 9. 使用限制

- **没有会话属性接口。** 连接级状态由使用方按会话自行维护；见第 4 节。
- **处理器是单例。** 同一个运行时只有一份处理器实例，回调可能并发执行，实例字段必须线程安全。
- **消息不保序。** 同一连接的多条消息可能并发处理；严格有序需要业务自行同步。
- **没有自动心跳。** 框架不会定时发送 Ping，需要保活时自行调用 `session.sendPing()`。
- **协议参数不可配。** 建造者只开放路径、处理器与线程模型；子协议、压缩、帧长度上限等沿用 Netty `WebSocketServerProtocolHandler` 的默认配置，当前没有开放配置入口。
- **裸帧接口不安全。** 框架不复制帧内容，引用计数与内存安全由使用方负责；除确有极致性能要求外，不建议使用 `UnSafeWebSocketHandler`。
- **路径是正则，不是路由模式。** 不支持 `@Route` 那样的 `{id}` 路径参数，参数从握手 uri 的查询串自行解析（`session.path()` 或 `session.headers()`）。

## 下一步

- [响应体系](./response.md)：SSE 推送与流式响应
- [快速入门](./quickstart.md)：HTTP 服务的启动与路由
- [框架参数调整](./config.md)：服务器与 HTTP 层的全局参数

[首页](../README.md) | [快速入门](./quickstart.md) | [响应体系](./response.md)
