# WebSocket

本轮重构只覆盖 HTTP 请求处理链路，WebSocket 模块没有纳入适配范围。

当前 `turboweb-websocket` 里仍是旧的处理器接口与连接信息模型，缺少与 `BootStrapWebServer`、`HttpRequestApplication` 配套的装配入口，文档中曾经出现的 `BootStrapTurboWebServer.protocol().websocket(...)` 注册方式已经不可用。现在接入 WebSocket 需要自行处理 Netty 管线，框架不提供开箱即用的注册通道。

因此本版本暂不推荐使用 WebSocket，本文档不提供示例，避免给出无法运行的写法。后续版本会按新的请求处理体系重新实现 WebSocket 的注册与生命周期管理，届时再补充对应文档。

需要服务端向客户端推送的场景，可以先用[响应体系](./response.md)中的 SSE 响应实现；只用 HTTP 轮询也能满足大部分非实时需求。

[首页](../README.md) | [响应体系](./response.md) | [请求体系](./request.md)
