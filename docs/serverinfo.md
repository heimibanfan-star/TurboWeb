# 运行信息的获取

TurboWeb 通过 `ServerInfoMiddleware` 中间件提供服务器运行状态监控能力，支持获取线程、内存和垃圾回收等关键指标。

首先需要注册该中间件：

```java
ServerInfoMiddleware serverInfoMiddleware = new ServerInfoMiddleware();
BootStrapTurboWebServer.create()
        .http().middleware(serverInfoMiddleware)
        .and().start();
```

访问端点：

| **参数**      | **说明**           | **示例请求**                                                |
| ------------- | ------------------ | ----------------------------------------------------------- |
| `type=thread` | 获取线程信息       | `GET http://localhost:8080/turboWeb/serverInfo?type=thread` |
| `type=memory` | 获取内存使用情况   | `GET http://localhost:8080/turboWeb/serverInfo?type=memory` |
| `type=gc`     | 获取垃圾回收器信息 | `GET http://localhost:8080/turboWeb/serverInfo?type=gc`     |

**_自定义访问路径_**

可通过 `setRequestPath()` 方法修改默认路径：

```java
ServerInfoMiddleware serverInfoMiddleware = new ServerInfoMiddleware();
serverInfoMiddleware.setRequestPath("/infos");
```

访问示例：

```http
GET http://localhost:8080/infos?type=gc
```



[首页](../README.md) | [模板技术的支持](./template.md) | [Cookie](./cookie.md)