# 快速入门——第一个“Hello World”

## 第一个“Hello World”

**_1.引入相关jar包_**

// TODO 待补充

**_2.编写Controller接口_**

```java
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/hello")
public class HelloController {
    
    @Get
    public String hello(HttpContext context) {
        return "Hello World";
    }
}
```

`@RequestPath("/hello")`：用于声明类级别的路由前缀。这个 Controller 对应的路径前缀是 `/hello`。

`@Get`：声明该方法只响应 HTTP GET 请求。

`public String hello(HttpContext context)`：方法参数固定为 `HttpContext`，你可以从中读取参数、写响应等。返回的 `"Hello World"` 将被直接写入 HTTP 响应体。

> TurboWeb 所有控制器方法统一只接收一个参数 `HttpContext`，开发者拥有完全控制权，框架不对参数做侵入式封装。

**_3.编写服务器启动类，注册Controller_**

```java
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        // 创建路由管理器
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new HelloController());
        // 配置并启动服务器
        BootStrapTurboWebServer.create()
                .http()
                .routerManager(routerManager)
                .and()
                .start();
    }
}
```

`AnnoRouterManager` 是 TurboWeb 提供的注解路由管理器，专门用于加载带有 `@RequestPath`、`@Get` 等注解的控制器。

`addController()` 将你定义的控制器注册到路由中。

`BootStrapTurboWebServer.create()` 开启服务器构建流程，`.http()` 表示进入 HTTP 服务配置阶段，在这一阶段设置路由管理器等参数。调用 `.and()` 返回主流程，最后通过 `.start()` 启动服务器并开始监听请求。



**_设置IO线程的数量_**

TurboWeb 的服务器构建器还提供了一个带参数的重载方法，用于设置服务器处理 IO 事件的线程数量：

```java
BootStrapTurboWebServer.create(8)
```

上面的代码表示将 IO 线程数量显式设置为 8。

在默认配置下，TurboWeb 采用**单线程 IO 模型**。这是因为在 TurboWeb 的架构中，IO 线程只负责处理网络事件，如连接建立、请求读取、响应写出等，**不会参与任何业务逻辑的执行**。

业务处理完全交由 **虚拟线程调度器** 执行，不阻塞、不抢占 IO 线程资源。这种设计让大多数场景下的 IO 操作都可以通过单线程完成，进一步提升了资源利用率与系统可控性。

你可以根据实际需求调整 IO 线程数量，例如在面对大量并发连接（如 WebSocket 或 SSE）时，适当提升线程数以获得更好的吞吐能力。但在大多数常规 HTTP 应用中，**单线程 IO 已经足够高效**。



**_设置监听端口和地址_**

TurboWeb的 `start()` 方法可以指定监听的端口和IP地址，默认监听0.0.0.0:8080。

如果要更改监听的端口可以调用如下的重载方法：

```java
start(8090);
```

如果要同时设置监听的IP和端口调用如下的重载方法：

```java
start("127.0.0.1", 8090);
```



[首页](../README.MD)

