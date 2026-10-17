# 快速入门

本文用最少的代码跑起一个 TurboWeb 服务：一个控制器、一个启动类，然后验证请求能通。

运行环境要求 JDK 21。

## 1. 引入依赖

Gradle（Kotlin DSL）：

```kotlin
dependencies {
    implementation("io.gitee.turboweb:turboweb-server:$turbowebVersion")
}
```

Maven：

```xml
<dependency>
    <groupId>io.gitee.turboweb</groupId>
    <artifactId>turboweb-server</artifactId>
    <version>${turboweb.version}</version>
</dependency>
```

版本号不要写死在文档里。Gradle 把 `turbowebVersion` 放进 `gradle.properties` 或版本目录统一管理，Maven 把 `turboweb.version` 放进 `properties`，取值以最新发布版本为准。

`turboweb-server` 会传递引入路由注解、上下文契约、序列化、校验等模块，普通 HTTP 服务只引它一个就够了。

## 2. 编写控制器

```java
package demo;

import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/hello")
public class HelloController {

    @Get
    public String hello() {
        return "Hello TurboWeb";
    }
}
```

这段代码里只有三条规则需要记住：

- `@Route` 标在类上，声明路由前缀；不写时表示没有前缀。
- `@Get` 标在公有方法上，声明该方法响应的请求方式与路径。路径默认值是 `/`，所以这里的最终路径就是 `/hello`。
- 方法的返回值就是响应体。返回 `String` 时按 `text/html` 写出，返回其他对象时按 JSON 写出。

同一个方法只能标一个请求方式注解。`@Post`、`@Put`、`@Patch`、`@Delete` 的用法与 `@Get` 相同。

## 3. 启动服务器

```java
package demo;

import top.turboweb.http.router.RestRouteRegister;
import top.turboweb.http.server.BootStrapWebServer;
import top.turboweb.http.server.application.HttpRequestApplication;

public class Main {

    public static void main(String[] args) {
        // 1. 登记控制器，生成路由表
        RestRouteRegister routeRegister = new RestRouteRegister();
        routeRegister.registerRoute(new HelloController());

        // 2. 装配 HTTP 应用
        HttpRequestApplication application = new HttpRequestApplication.Builder()
                .routeRegister(routeRegister)
                .build();

        // 3. 注册应用并启动
        BootStrapWebServer server = new BootStrapWebServer();
        server.registerApplicationLast(application);
        server.start(8080);
    }
}
```

三个对象各管一件事：

- `RestRouteRegister`：收集控制器和各项扩展配置。`registerRoute(instance)` 以实例的类扫描路由注解。需要统一前缀时用 `new RestRouteRegister("/api")`，上例的路径会变成 `/api/hello`。
- `HttpRequestApplication`：一个 HTTP 应用，负责每条新连接的处理器装配。中间件、异常处理器、Session 管理器、JSON 序列化器、CORS、结果转化器、响应写出器以及 `HttpConfig` 都在它的 `Builder` 上配置。
- `BootStrapWebServer`：服务器本体，负责监听端口、管理连接与线程资源。应用必须在 `start` 之前注册。

## 4. 验证

启动后请求：

```bash
curl http://localhost:8080/hello
```

得到：

```
Hello TurboWeb
```

路径不存在时返回 404，响应体是框架生成的错误页面。

## 5. 常用启动参数

**_监听地址与端口_**

```java
server.start(9090);                    // 监听 0.0.0.0:9090
server.start("127.0.0.1", 9090);       // 只监听本机
```

`start` 返回 Netty 的 `ChannelFuture`，是异步绑定。需要确认端口绑定成功后再继续时，调用 `.sync()`。

**_调整 IO 线程数_**

服务器配置由 `ServerConfig` 承担：

```java
import top.turboweb.http.server.config.ServerConfig;

ServerConfig config = ServerConfig.builder()
        .ioThreadNum(4)
        .maxConnect(20000)
        .build();

BootStrapWebServer server = new BootStrapWebServer(config);
```

默认 IO 线程数为 1。IO 线程只处理网络事件，业务逻辑跑在虚拟线程上，常规 HTTP 服务不必调大。

HTTP 层参数（字符集、Session 回收、请求体串行化、磁盘 IO 线程池等）走 `HttpConfig`，配置方式见[框架参数调整](./config.md)。

**_关闭服务器_**

`BootStrapWebServer` 实现了 `AutoCloseable`。`close()` 会先关闭监听通道，再停掉 boss/worker 线程组，最后按注册顺序关闭全部应用，释放中间件与线程池等资源。

```java
Runtime.getRuntime().addShutdownHook(new Thread(server::close));
```

也可以在容器或测试里直接 try-with-resources。

## 下一步

- [路由体系](./router.md)：路径规则、路径参数、多请求方式
- [请求体系](./request.md)：查询参数、表单、JSON、路径参数与文件上传
- [响应体系](./response.md)：状态码、响应头、流式响应

[首页](../README.md) | [路由体系](./router.md)
