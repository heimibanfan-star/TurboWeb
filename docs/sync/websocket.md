# <img src="../image/logo.png"/>

# WebSocket的支持

WebSocket 是一种网络通信协议，基于 TCP 连接，实现客户端与服务器之间的全双工（双向）实时通信。与传统的 HTTP 请求-响应模式不同，WebSocket 在建立连接后，客户端和服务器可以随时相互发送数据，无需每次都重新建立连接。它广泛应用于即时消息、在线游戏、实时通知等需要低延迟、高频交互的场景，极大提升了网络通信效率和用户体验。

TurboWeb 的 WebSocket 支持设计简洁高效。它直接基于 Netty 实现，提供轻量级的连接管理和消息收发能力。框架通过动态添加和移除处理器，按需开启 WebSocket 功能，避免资源浪费。消息推送和接收均采用异步非阻塞机制，保证高并发下的低延迟和稳定性。TurboWeb 还提供便捷的会话管理接口，方便开发者实现实时双向通信功能。整体设计注重性能和扩展性，适合构建大规模实时交互应用。

## WebSocket的使用

TurboWeb默认**不开启**WebSocket，只有当开发者添加WebSocket处理器的时候WebSocket才会自动被启用。

接下来咱们先使用TurboWeb最原生的方式使用WebSocket。

创建一个WebSocket处理器，实现 `WebSocketHandler` 接口：

```java
package org.example.websocketexample;

import io.netty.handler.codec.http.websocketx.*;
import top.turboweb.websocket.WebSocketHandler;
import top.turboweb.websocket.WebSocketSession;


public class OneWebSocketHandler implements WebSocketHandler {
	@Override
	public void onOpen(WebSocketSession session) {
		String websocketPath = session.getWebSocketConnectInfo().getWebsocketPath();
		System.out.println("onOpen: " + websocketPath);
	}

	@Override
	public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
		try {
			if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
				System.out.println("onMessage: " + textWebSocketFrame.text());
				session.sendText(textWebSocketFrame.text());
			} else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
				System.out.println("onMessage: " + binaryWebSocketFrame.content());
			} else if (webSocketFrame instanceof PingWebSocketFrame pingWebSocketFrame) {
				System.out.println("onMessage: " + pingWebSocketFrame.content());
			} else if (webSocketFrame instanceof PongWebSocketFrame pongWebSocketFrame) {
				System.out.println("onMessage: " + pongWebSocketFrame.content());
			}
		} finally {
			webSocketFrame.release();
		}

	}

	@Override
	public void onClose(WebSocketSession session) {
		System.out.println("onClose");
	}
}
```

`onMessage(..)` 方法是收到客户端发送的数据帧而触发的回调，由于TurboWeb在接收到数据帧时会直接交给WebSocket分发器异步执行，因此netty就无法自动释放 `WebSocketFrame` 因此需要开发者手动调用 `webSocketFrame.release();` 来释放。

注册websocket处理器：

```java
public class WebSocketApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(WebSocketApplication.class);
		server.websocket("/ws", new OneWebSocketHandler());
		server.start();
	}
}
```

看到如下的日志：

```text
14:55:11 [INFO ] [main] t.t.w.d.WebSocketDispatcherHandler - websocket work on VirtualThread
14:55:11 [INFO ] [main] t.t.c.i.i.DefaultWebSocketHandlerInitializer - websocket处理器初始化成功
```

表面websocket处理器注册成功，默认采用虚拟线程来处理接收到的消息。

接下来创建前端页面发送websocket数据：

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>websocket</title>
</head>
<body>
<script>
    const ws = new WebSocket('ws://localhost:8080/ws');
    ws.onopen = function (event) {
        ws.send('Hello World')
    };
    ws.onmessage = function (event) {
        console.log(event.data)
        ws.close()
    }
</script>
</body>
</html>
```

这里当接收到服务端一条消息时直接关闭连接，查看控制台：

```text
onOpen
onMessage: Hello World
onClose
```

说明客户端向服务器推送成功，服务器也向客户端推送成功，因为之后服务器推送给客户端消息之后才会关闭。

例如，上述例子中，客户端连接地址是 `ws://localhost:8080/ws`，连接建立后才能正常通信。但在实际应用中，我们往往需要在连接路径上携带不同参数，这时精确路径匹配就显得不够灵活。

TurboWeb 的 WebSocket 路径支持正则表达式匹配，比如：

```java
server.websocket("/ws/(.*)", new OneWebSocketHandler());
```

这样，所有以 `ws://localhost:8080/ws/` 开头的路径都能被正常连接。同时，我们还可以通过 `WebSocketSession` 获取实际连接的路径，示例代码如下：

```java
@Override
public void onOpen(WebSocketSession session) {
    String websocketPath = session.getWebSocketConnectInfo().getWebsocketPath();
    System.out.println("onOpen: " + websocketPath);
}
```

这为根据不同路径参数进行灵活处理提供了便利。

上面原生的写法websocket显得有点繁琐了，开发者不仅仅需要**判断帧类型**还需要**手动释放内存**，如果开发者开发的过程中忘记了释放内存，那么就很容易导致内存泄漏，因此TurboWeb提供了一个更高的抽象层，**会自动根据不同的数据帧分发数据**，并且也会**自动管理内存的释放**。

如下列代码：

```java
public class TwoWebSocketHandler extends AbstractWebSocketHandler {
	@Override
	public void onText(WebSocketSession session, String content) {
		System.out.println("收到文本消息: " + content);
	}

	@Override
	public void onBinary(WebSocketSession session, ByteBuf content) {
		System.out.println("收到二进制消息: " + content);
	}
}
```

在 `AbstractWebSocketHandler` 需要开发者实现 `onText(..)` 方法和 `onBinary(..)` 方法，其余的方法有默认的实现，开发者可以选择性的去实现。

需要注意的是，如果在 `onBinary(..)` 方法中 `Bytebuf` 需要异步传递，那么需要调用如下的方法：

```java
content.retain();
```

然后在异步线程使用完毕之后手动的去释放。



[目录](./guide.md) [SSE的支持](./sse.md) 上一节 下一节 [HTTP客户端]()