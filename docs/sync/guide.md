# <img src="../image/logo.png"/>
# TurboWeb的同步风格编程

## 什么是同步风格编程？

TurboWeb 的同步风格，是指采用传统阻塞式编程模型开发 HTTP 接口和业务逻辑的方式。开发者可以像在 Servlet 或 Spring Boot 中一样，**用最自然、最直观的代码写法** 来处理请求，无需理解响应式流、回调或背压机制。

而 TurboWeb 能够在保持同步写法的同时，实现高并发和非阻塞的原因，是它底层默认采用 **JDK 21 的虚拟线程（Loom）调度器**。

## 指导手册

[1.快速入门](./quickstart.md)

[2.请求数据的处理](./request.md)

[3.响应数据的处理](./response.md)

[4.路由的支持](./router.md)

[5.文件的处理](./file.md)

[6.异常处理器](./exceptionhandler.md)

[7.中间件的使用](./middleware.md)

[8.Cookie](./cookie.md)

[9.Session](./session.md)

[10.SSE的支持](./sse.md)

[11.WebSocket的支持](./websocket.md)

[12.HTTP客户端](./client.md)

[13.服务器参数配置](./serverconfig.md)

[14.生命周期相关](./lifecycle.md)

[15.节点共享](./nodeshare.md)





[首页](../../README.md) 
