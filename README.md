# <img src=".\docs\image\logo.png"/>
>  你还在纠结“同步代码写得爽”还是“异步框架性能强”？
>
> **别选了，全都要！**
>
> **TurboWeb** —— 用同步的姿势，干掉异步的活儿！



## TurboWeb是什么？

**TurboWeb** 是一个现代、高性能、无废话的 Java Web 框架，底层基于 Netty，核心依托 **JDK 21 虚拟线程（Loom）**，为高并发场景而生。它不仅运行得飞快，还写得极爽——

你写的，是再普通不过的同步 Java 代码；
你收获的，是让异步框架都汗颜的吞吐性能。

不再需要回调、响应式、链式 API，也不必掌握 Mono、Flux 或什么“编排式编程”——只要用好一个 `HttpContext`，剩下的，都交给 TurboWeb。

## 为什么选择TurboWeb？

**_同步开发体验，异步级性能_**

TurboWeb 全面基于 **虚拟线程** 执行请求处理，不再使用传统线程池。每个请求拥有独立线程，上万并发依旧从容，开发体验丝滑如春风。

**_中间件驱动的灵活架构_**

TurboWeb 拒绝内置臃肿，所有功能都以中间件形式插件化集成：跨域处理、限流、模板渲染、甚至是 controller 本身，都是中间件。你只引入你用到的，它就只干你想干的。

**_长连接原生支持_**

WebSocket 与 SSE 原生集成，无需第三方依赖即可构建 IM、推送、实时控制等高连接数应用，底层生命周期由 Netty 驱动，性能轻盈又稳定。

**_极简路由与控制器_**

使用注解声明路由，所有控制器方法的参数统一为 `HttpContext`，你拥有 100% 控制权。框架通过**方法句柄**（非反射）进行方法调用，性能与手写代码无异。

**_去中心化服务间通信_**

TurboWeb 不依赖传统中心化网关。通过“服务间路由共享”，每个节点都可以对外暴露接口，轻松构建更加**去中心化、弹性更强**的微服务架构。

**_文件下载支持伪异步I/O_**

传统零拷贝虽然高效，但强依赖 Netty IO 线程。TurboWeb 采用更稳健的**伪异步传输模型**，确保业务线程控制文件读取，**性能与稳定性并存**，避免阻塞关键 IO 线程。

**_最简启动，极速响应_**

无需 XML，无需注解扫描，几行代码即可跑通请求流程。框架启动只需百毫秒，占用几十 MB，适合快速开发、小型部署，也适合重型分布式集群服务。



**TurboWeb**，不只是一个框架，更是一种**”用同步思维写异步应用”**的全新范式。

准备好了吗？
是时候用同步代码迎接高并发挑战，
和 TurboWeb 一起，重构你的 Web 开发体验。



## 目录

[快速入门——第一个“Hello World”](./docs/quickstart.md)

[路由体系](./docs/router.md)

[请求数据的处理](./docs/request.md)

[响应数据的处理](./docs/response.md)

[文件的上传和下载](./docs/file.md)

[异常处理器](./docs/exceptionhandler.md)

[中间件的使用](./docs/middleware.md)

[拦截器的使用](./docs/interceptor.md)

[静态资源的支持](./docs/staticresource.md)

[模板技术的支持](./docs/template.md)

[运行信息的获取](./docs/serverinfo.md)

[Cookie](./docs/cookie.md)

[Session](./docs/session.md)

[Server-Sent Events](./docs/sse.md)

[WebSocket](./docs/websocket.md)

[嵌入式网关](./docs/gateway.md)
