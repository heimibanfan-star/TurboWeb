# HTTP 客户端

`turboweb-http-client` 内部已完成响应式引擎重构：新的实现是 `ReactiveHttpClientEngine` 一系与 `RestHttpClient` 门面，移除了 httpcore5 依赖。但该模块当前处于暂不支持的阶段：本版本没有为它提供使用文档与示例，也没有随服务端体系做配套的回归验证，暂不推荐在生产使用。

只需要调用第三方 HTTP 接口时，可以继续使用 JDK 自带的 `java.net.http.HttpClient`，它与 TurboWeb 的服务端链路没有耦合。后续版本会为客户端模块补充文档与示例，届时再更新本页。

[首页](../README.md) | [快速入门](./quickstart.md)
