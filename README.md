<br/><br/>


<p align="center">
 <img src="./docs/image/logo.png" alt=""/>
</p>

<div align="center">

![Gitee Stars](https://gitee.com/turboweb/turboweb/badge/star.svg?theme=dark)
![Forks](https://gitee.com/turboweb/turboweb/badge/fork.svg?theme=dark)
![License](https://img.shields.io/badge/License-Apache--2.0-red)

</div>

<br/><br/><br/>

> 你还在纠结“同步代码写得爽”还是“异步框架性能强”？
>
> **别选了，全都要！**
>
> **TurboWeb** —— 用同步的姿势，干掉异步的活儿！


## TurboWeb是什么？

**TurboWeb** 是一个现代、高性能、无废话的 Java Web 框架，底层基于 Netty，核心依托 **JDK 21 虚拟线程（Loom）**，为高并发场景而生。它不仅运行得飞快，还写得极爽——

你写的，是再普通不过的同步 Java 代码；
你收获的，是让异步框架都汗颜的吞吐性能。

不再需要回调、响应式、链式 API，也不必掌握 Mono、Flux 或什么“编排式编程”。路由用 `@Route`、`@Get` 声明，形参用取参注解直接声明业务类型，剩下的，都交给 TurboWeb。

## 为什么选择TurboWeb？

**_高度可定制_**

请求处理的每个环节都留了扩展接口：中间件链、参数解析器、内容解析器、JSON 序列化器、结果转化器、响应写出器、异常处理器都可以注册或替换，路由登记表还开放 Bean 校验器与解析器顺序调整。默认实现覆盖常规场景，需要改行为时不必修改框架源码。

**_同步开发体验，异步级性能_**

TurboWeb 全面基于 **虚拟线程** 执行请求处理，不再使用传统线程池。每个请求拥有独立线程，上万并发依旧从容，开发体验丝滑如春风。

**_长连接原生支持_**

SSE 原生集成，无需第三方依赖即可构建推送类应用，底层生命周期由 Netty 驱动，性能轻盈又稳定。WebSocket 本轮重构暂未适配，不推荐使用。

**_注解路由与注解取参_**

`@Route` 声明类前缀，`@Get`、`@Post` 等方法声明请求方式与路径；形参用 `@PathParam`、`@QueryParam`、`@BeanJson` 等注解直接声明业务类型，不必从上下文手工取数。框架通过**方法句柄**调用控制器，路径与参数在构建期完成校验。

**_文件下载的稳定支持_**

TurboWeb 在进行文件下载时可以实现零拷贝传输而不阻塞 Netty 的 IO 线程，同时保留 AIO 与分块流式两种写出方式作为稳定性补充，在磁盘 I/O 波动或特殊场景下可切换使用，确保文件下载在高并发环境下依然流畅稳定。

**_最简启动，极速响应_**

无需 XML，无需扫描 classpath，几行代码即可跑通请求流程。框架启动只需百毫秒，占用几十 MB，适合快速开发、小型部署，也适合重型分布式集群服务。



**TurboWeb**，不只是一个框架，更是一种**”用同步思维写异步应用”**的全新范式。

准备好了吗？
是时候用同步代码迎接高并发挑战，
和 TurboWeb 一起，重构你的 Web 开发体验。



## 本版本变更说明

2.5.0 系列是服务端请求处理链路的一次整体重构，旧写法大多不能直接编译通过。各章开头的“变更说明”记录了对应范围，升级前建议先对照。

- 路由只保留注解一种写法：`@Route` 声明类前缀，`@Get`、`@Post` 等方法声明请求方式与路径。`@RequestPath` 与 lambda 编程式路由下线，路径匹配与冲突校验统一在构建期完成。
- 取参从 `HttpContext` 的方法族收敛为注解形参：`@PathParam`、`@QueryParam`、`@HeaderParam`、`@CookieParam`、`@FormParam`、`@BeanQuery`、`@BeanForm`、`@BeanJson`。上传文件并入 `@FormParam`，旧的 `@UploadParam` 下线。
- 响应处理拆成结果转化与响应写出两段，各自开放注册入口；字符集补写与文件响应编码集中到转化出口。
- 启动入口改为 `BootStrapWebServer` 与 `HttpRequestApplication`，旧的 `BootStrapTurboWebServer` 下线；服务器参数与应用参数分别由 `ServerConfig`、`HttpConfig` 承载。
- 新增参数解析器、内容解析器、序列化器、结果转化器、响应写出器五类定制入口，写法见目录末尾的文档。
- 静态资源处理中间件等旧组件已下线；HTTP 客户端与 WebSocket 本轮未纳入适配范围，暂不推荐使用。

## 本版本特色

- **高度可定制**：请求处理的每个环节都留了接口。参数解析器、内容解析器、JSON 序列化器、Bean 校验器、结果转化器、响应写出器、中间件、异常处理器都可以替换或追加，注册集中在 `HttpRequestApplication.Builder` 与 `RestRouteRegister`，构建期一次装配完成。写法见目录末尾的三份定制文档。
- **同步写法，虚拟线程执行**：控制器方法运行在各自的虚拟线程上，阻塞式 I/O 不必改写成响应式；调度限流按需限制并发虚拟线程数。
- **注解路由与方法句柄调用**：路径、请求方式、取参全部由注解声明，构建期生成调用链，运行期不做反射查找。
- **内置取参方式开箱可用**：路径、查询、请求头、Cookie、表单与上传文件，另有 `@BeanQuery`、`@BeanForm`、`@BeanJson` 三种 bean 绑定；框架对象按形参类型直接注入。
- **完整的响应形态**：字符串、对象 JSON、`HttpResult`、任意 `HttpResponse`、文件下载（零拷贝、AIO、分块流式）、`Publisher` chunked 流与 SSE。
- **请求体先解析后绑定**：请求体按 `Content-Type` 在路由匹配前解析为内容对象，参数绑定器认具体内容类取数；新类型只需补一条内容解析器。
- **构建期校验**：路由冲突、重复注册、参数解析器与内容解析器冲突都在 `build()` 时暴露，不会拖到运行期才报错。



## 目录

[快速入门——第一个“Hello World”](./docs/quickstart.md)

[路由体系](./docs/router.md)

[请求体系](./docs/request.md)

[响应体系](./docs/response.md)

[异常处理器](./docs/exceptionhandler.md)

[中间件的使用](./docs/middleware.md)

[Cookie](./docs/cookie.md)

[Session](./docs/session.md)

[WebSocket](./docs/websocket.md)

[框架参数调整](./docs/config.md)

[HTTP客户端](./docs/httpclient.md)

[定制：参数解析器](./docs/custom-parameter-parser.md)

[定制：响应写出策略](./docs/custom-response-writing.md)

[定制：序列化与实体校验](./docs/custom-serializer-validation.md)


## 联系我们

QQ 群：154319949