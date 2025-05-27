# <img src="../image/logo.png"/>

# 服务器参数配置

开发者可以根据不同的需求对服务器的参数进行配置。

## 配置IO线程的数量

```java
new StandardTurboWebServer(ParamApplication.class, 1);
```

``StandardTurboWebServer`` 构造器的第二个参数就是指定IO线程的数量，默认情况下IO线程是以单线程允许，由于IO线程不执行任何阻塞操作，因此少量即可，开发者可以根据需求自行配置。

## 服务实例参数配置

```java
public static void main(String[] args) {
    TurboWebServer server = new StandardTurboWebServer(ParamApplication.class, 1);
    server.config(config -> {
        // TODO 由此进行参数配置
    });
}
```

在TurboWeb中服务实例的参数配置由 `TurboWebServer` 的 `config(..)` 方法进行配置，参数是一个回调，TurboWeb会传入配置对象。

下面来说一下可以配置哪些内容。

**设置请求封装时的字符集编码**

```java
config.setCharset(StandardCharsets.UTF_8);
```

**设置最大接收请求内容的字节数**

```java
config.setMaxContentLength(1024 * 1024 * 10);
```

通过该配置可以间接限制文件上传的大小。

**显示请求响应时间日志**

```java
config.setShowRequestLog(true);
```

**Session相关的配置**

TurboWeb会自动进行Session的检测清除过期和不活跃的Session来优化内存。

设置Session检查触发的时间间隔，默认是5分钟：

```java
config.setSessionCheckTime(300000);
```

设置Session多长时间内没有被使用就被认为过期，默认不限时，也就是-1：

```java
config.setSessionMaxNotUseTime(-1);
```

设置Session检查的阈值，只有到达检查触发时间，并且Session数量到达阈值才会进行垃圾清理：

```java
config.setCheckForSessionNum(256);
```

**设置反应式调度器的线程数量**

```java
config.setReactiveServiceThreadNum(16);
```

由于TurboWeb对反应式调度器的支持目前来说不是很完善，因此该参数不需要设置，目前也不推荐使用反应式调度器。

## WebSocket调度线程的切换

默认情况下，如果只传入WebSocket处理器，那么TurboWeb默认会选择使用虚拟线程来处理WebSocket接收到的数据帧。

但是用户可以切换为Forkjoin线程池，具体的切换方式如下：

```java
server.websocket("/ws/(.*)", new OneWebSocketHandler(), 8);
```

传入第三个参数并且设置Forkjoin线程的数量即可，日志如下：

```text
16:53:37 [INFO ] [main] t.t.w.d.WebSocketDispatcherHandler - websocket work on ForkJoin pool, threadNum: 8
16:53:37 [INFO ] [main] t.t.c.i.i.DefaultWebSocketHandlerInitializer - websocket处理器初始化成功
```

现在TurboWeb接收到客户端发送的WebSocket数据帧会交给Forkjoin来异步处理，**推荐使用默认的虚拟线程处理**。



[目录](./guide.md) [HTTP客户端](./client.md) 上一节 下一节 [生命周期相关](./lifecycle.md)

