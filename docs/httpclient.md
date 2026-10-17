# HTTP 客户端

本轮重构只覆盖服务端 HTTP 请求处理链路，`turboweb-client` 没有纳入适配范围。

客户端模块仍是旧的调用面，原文示例依赖已经下线的服务端取参 API（如 `ctx.loadQuery(...)`、`ctx.loadForm(...)`），不能直接运行。本轮也没有为新版 `BootStrapWebServer`、`HttpRequestApplication` 提供配套的客户端配置与回归验证。

因此本版本暂不推荐使用 HTTP 客户端，本文档不提供示例，避免给出无法运行的写法。后续版本会按新的服务端体系重新整理客户端接口，届时再补充对应文档。

只需要调用第三方 HTTP 接口时，可以继续使用 JDK 自带的 `java.net.http.HttpClient`，它与 TurboWeb 的服务端链路没有耦合。

[首页](../README.md) | [快速入门](./quickstart.md)
