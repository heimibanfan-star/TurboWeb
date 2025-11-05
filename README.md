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

> **使用案例: 飞廉管理系统**
> 
> 仓库地址: https://gitee.com/project-development_6/feilian


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

**_文件下载的稳定支持_**

TurboWeb在进行文件下载的时候，可以实现零拷贝传输而不阻塞Netty的IO线程，同时仍保留 伪异步 I/O 模式 作为稳定性补充，在磁盘 I/O 波动或特殊场景下可切换使用，确保文件下载在高并发环境下依然流畅稳定。

**_最简启动，极速响应_**

无需 XML，无需注解扫描，几行代码即可跑通请求流程。框架启动只需百毫秒，占用几十 MB，适合快速开发、小型部署，也适合重型分布式集群服务。



**TurboWeb**，不只是一个框架，更是一种**”用同步思维写异步应用”**的全新范式。

准备好了吗？
是时候用同步代码迎接高并发挑战，
和 TurboWeb 一起，重构你的 Web 开发体验。



## 新特性

**HTTP2.0的无感知适配**

新增对HTTP2.0的无感知支持，若开启HTTP2.0，框架会自动注册协商器，根据客户端的情况选择使用HTTP1.1还是HTTP2.0，若客户端支持HTTP2.0则通过适配层将HTTP2.0的Frame转化为标准的HTTP1.1的请求响应对象，对上层无任何感知。

**对嵌入式网关进行重构**

将嵌入式网关作为一个单独的Handler，支持本地中转、远程中转以及WebSocket双向中转；工程化节点的注册与配置；引入过滤器，便于实现网关鉴权；同时提供断路器，对一些故障服务快速降级，防止服务雪崩。

**简洁的 HTTP 客户端**

基于 `reactor-netty` 构建，提供类似 **Axios** 的调用风格，提升易用性和开发效率。

**新增注解 `@Route`**

与 `@RequestPath` 作用一致，提供更简洁的路由声明方式。

**声明式路由管理器升级**

对参数的自动注入功能进行增强，高度解耦，支持动态添加参数封装与解析方式。

**统一路径前缀支持**

声明式路由管理器可统一设置路径前缀，减少重复定义。

**静态资源处理中间件重构**

支持 **Range 传输**（断点续传、分块请求），支持直接映射磁盘文件，提高静态资源访问性能。

**新增部分特殊中间件**

版本控制中间件（可根据不同逻辑选择不同的路由管理器）；分支中间件（支持基于条件动态选择分支，并最终合并回主分支）；类型关注中间件（实现对上游特殊返回值类型的关注和忽略）。

**HTTPS配置的简化**

通过ssl方法可以直接配置HTTPS所需的证书，框架自动会关闭零拷贝线程池，减少handler手动注册的复杂度。

**重构Trie匹配树**

实现标准化的Trie，可以很方便的对Trie进行扩展和根据开发者自己的需求开发一个Trie，将对Trie的匹配逻辑由递归的方式改为迭代的方式，防止栈溢出。




## 整合SpringBoot
TurboWeb提供了与SpringBoot的整合包，详细教程看如下仓库:
[TurboWeb-SpringBoot](https://gitee.com/turboweb/turboweb-springboot)



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

[监听器](./docs/listener.md)

[服务器参数配置](./docs/config.md)

[三级限流保护体系](./docs/limiter.md)

[HTTPS](./docs/https.md)

[走向HTTP2.0](./docs/http2.md)

[HTTP客户端](./docs/httpclient.md)

[多版本路由控制](./docs/mvrc.md)


## 联系我们

QQ 群：154319949