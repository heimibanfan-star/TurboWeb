# 框架参数调整

TurboWeb 的可调参数分两个对象存放：

- `ServerConfig`：服务器层参数，决定监听线程、连接上限、协议、请求体大小与连接空闲回收，构造 `BootStrapWebServer` 时传入；
- `HttpConfig`：HTTP 应用层参数，决定单连接在途响应上限、会话回收、调度限流与缺省字符集，通过 `HttpServerRuntime.builder().config(...)` 传入。

两个配置都是不可变对象，用建造者组装，链式方法名与参数同名。取值非法时在调用链式方法的那一刻就抛 `IllegalArgumentException`，不会拖到启动时才报错。

Session 管理器、JSON 序列化器、中间件、异常处理器、内容解析器、CORS、任务卸载执行器属于组件对象，不放在这两个配置里，各有 `HttpServerRuntime.Builder` 上的注册方法。

## 变更说明

2.6.x 的参数面有增有删：

- `serializePerConnection` 下架：同连接响应定序改由写出管线固定承担，不再是可选开关；单连接在途响应的深度改由新参数 `maxInFlightResponses` 控制（默认 8），见 1.1。
- 磁盘 IO 线程池三参数（`diskOpeThreadCoreQueue`、`diskOpeThreadCacheQueue`、`diskOpeThreadMaxThreadNum`）下架：2.6 没有独立磁盘 IO 池，流式写出走任务卸载执行器，要接管时注册自定义 `MicroTaskExecutor`。
- `ServerConfig` 新增 `idleCloseTime`：连接空闲回收阈值，见 3.4。
- 调度限流四参数保留，语义不变；限流的承载从旧调度器换成了微任务执行器的虚拟线程实现。
- 应用装配入口由 `HttpRequestApplication.Builder` 改为 `HttpServerRuntime.builder()`，配置通过 `config(...)` 整体传入。

## 1. 应用级参数：HttpConfig

```java
import top.turboweb.http.server.HttpServerRuntime;
import top.turboweb.http.server.config.HttpConfig;

import java.nio.charset.StandardCharsets;

HttpConfig config = HttpConfig.builder()
        .maxInFlightResponses(8)
        .sessionCheckTime(60_000)
        .sessionMaxNotUseTime(1_800_000)
        .sessionCheckThreshold(256)
        .enableHttpSchedulerLimit(true)
        .httpSchedulerLimitCount(2_000)
        .httpSchedulerLimitCacheThread(2_000)
        .httpSchedulerLimitTimeout(500)
        .defaultCharset(StandardCharsets.UTF_8)
        .build();

HttpServerRuntime runtime = HttpServerRuntime.builder()
        .routeRegister(routeRegister)
        .config(config)
        .build();
```

全部参数：

| 方法 | 默认值 | 约束 | 作用 |
| --- | --- | --- | --- |
| `maxInFlightResponses` | `8` | 大于 `0` | 单条连接在途响应的深度上限 |
| `showRequestLog` | `true` | 无 | 请求日志开关，当前版本未接入 |
| `sessionCheckTime` | `300000` | 不小于 `1000` | 会话回收检查间隔，毫秒 |
| `sessionMaxNotUseTime` | `-1` | 无 | 会话最大空闲时长，毫秒 |
| `sessionCheckThreshold` | `256` | 不小于 `1` | 会话数达到该值才执行回收 |
| `enableHttpSchedulerLimit` | `false` | 无 | 是否限制同时执行的请求数 |
| `httpSchedulerLimitCount` | `10000` | 不小于 `0` | 同时执行的请求上限 |
| `httpSchedulerLimitCacheThread` | `2000` | 不小于 `0` | 超限后允许排队等待的请求数 |
| `httpSchedulerLimitTimeout` | `500` | 不小于 `0` | 排队等待的超时时间，毫秒 |
| `defaultCharset` | `UTF-8` | 非 `null` | 缺省字符集 |

### 1.1 单连接在途响应上限

同一连接上的请求并发处理，响应由写出管线按序列定序写出：请求还没轮到写出时，它就处于“在途”状态。`maxInFlightResponses` 限制单条连接在途响应的深度，默认 8。

```java
HttpConfig.builder()
        .maxInFlightResponses(16)
        .build();
```

深度超限时，框架认为这条连接的客户端消费过慢，直接关闭连接并释放积压请求，而不是无限缓冲。慢客户端因此不会拖垮服务器，但会断线重连；对吞吐与内存都敏感的场景可适当调大。

这个上限与调度限流（1.4）互补：一个约束单条连接的积压，一个约束全服务同时执行的请求量。两道闸门与通道可写性节流的完整机制见[背压与事件卸载](./backpressure-offload.md)。

### 1.2 请求日志

请求日志由编排器在响应写出完成的回调里打印，内容是请求方法、URI、耗时和写出成败，日志级别为 DEBUG。是否输出取决于日志框架的级别配置。

`showRequestLog` 当前没有被处理器读取，改这个值不会改变日志输出。保留它是为了后续接入独立的开关。

### 1.3 会话回收

三个参数传入 `SessionManagerHolder`，控制内存会话的回收线程：

| 参数 | 说明 |
| --- | --- |
| `sessionCheckTime` | 回收线程的检查周期 |
| `sessionMaxNotUseTime` | 空闲超过该时长的会话被判为过期 |
| `sessionCheckThreshold` | 会话总数低于该值时跳过本轮检查 |

默认 `sessionMaxNotUseTime` 为 `-1`，对任何会话都成立。会话数一旦达到阈值，第一次回收会清掉全部会话，需要会话长期存活时必须显式配成正数。回收的具体过程与自定义 `SessionManager` 的注意事项见 [Session](./session.md)。

### 1.4 虚拟线程调度限流

控制同时执行业务的请求数量，默认关闭。

```java
HttpConfig.builder()
        .enableHttpSchedulerLimit(true)
        .httpSchedulerLimitCount(1000)       // 同时执行的请求上限
        .httpSchedulerLimitCacheThread(2000) // 超限后允许排队的请求数
        .httpSchedulerLimitTimeout(1000)     // 排队超时，毫秒
        .build();
```

关闭时，每个请求直接在一个新建虚拟线程里执行，不排队也不拒绝。

开启后，请求经受限的微任务执行器提交：

- 许可还有剩余，请求直接执行；
- 许可用完，排队名额未满，请求在虚拟线程里挂起等待，最多等 `httpSchedulerLimitTimeout` 毫秒；
- 等到许可就执行，等不到或者排队名额已满，请求提交不被受理，编排器直接写出 429，正文为 `too many request`。

`httpSchedulerLimitCount` 是同时执行的数量，不是每秒请求数。排队名额或超时设为 0 时不允许排队，资源不足的请求立刻得到 429。

需要按 QPS 限速时，在中间件里按时间窗口自己计数，这套参数不提供速率控制。事件卸载与自定义执行器的接管写法见[背压与事件卸载](./backpressure-offload.md)。

### 1.5 缺省字符集

`defaultCharset` 的默认值是 UTF-8，在四个地方生效：

- 请求体的 `Content-Type` 没有声明 `charset` 时，按它解码；
- 路径参数与查询参数一律按它解码，不看请求头；
- `String`、JSON 等文本响应的正文按它编码；
- Cookie 的值、下载文件的文件名按它编码。

请求头里声明了 `charset` 时，只影响请求体解码，其余三处仍然取缺省字符集。

## 2. 跨域配置：HttpCorsConfig

CORS 由 `HttpCorsConfig` 描述，通过运行时建造者的 `corsConfig(...)` 传入：

```java
import top.turboweb.http.orchestrator.HttpCorsConfig;
import top.turboweb.http.server.HttpServerRuntime;

import java.util.List;

HttpCorsConfig cors = HttpCorsConfig.builder()
        .allowedOrigins(List.of("https://admin.example.com"))
        .allowedMethods(List.of("GET", "POST", "PUT", "DELETE"))
        .allowedHeaders(List.of("Content-Type", "Authorization"))
        .exposedHeaders(List.of("Content-Disposition"))
        .allowCredentials(true)
        .maxAge(600)
        .build();

HttpServerRuntime runtime = HttpServerRuntime.builder()
        .routeRegister(routeRegister)
        .config(config)
        .corsConfig(cors)
        .build();
```

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `allowedOrigins` | `["*"]` | 允许的来源。通配项放行任意来源，否则要求精确相等 |
| `allowedMethods` | `["GET", "POST", "PUT", "DELETE"]` | 允许的方法，通配时写 `*` |
| `allowedHeaders` | `["*"]` | 允许的请求头，通配时写 `*` |
| `exposedHeaders` | `["Content-Disposition"]` | 允许浏览器读取的响应头 |
| `allowCredentials` | `false` | 是否允许携带 Cookie 等凭证 |
| `maxAge` | `3600` | 预检结果缓存时间，秒 |

来源通过校验时，响应写出的 `Access-Control-Allow-Origin` 是请求的实际来源，不是字面量 `*`。来源不在白名单内时不写允许来源、方法、请求头等头。`allowCredentials` 为 `true` 时先写 `Access-Control-Allow-Credentials: true`；如果请求来源恰好是字面量 `*`，按规范整体跳过，不写任何 CORS 头。

CORS 头的写出时机与 OPTIONS 预检的处理见[响应体系](./response.md)。

## 3. 服务器级参数：ServerConfig

```java
import io.netty.handler.codec.http2.Http2Settings;
import top.turboweb.http.server.BootStrapWebServer;
import top.turboweb.http.server.config.ServerConfig;

ServerConfig serverConfig = ServerConfig.builder()
        .maxConnect(20_000)
        .ioThreadNum(2)
        .cpuNum(8)
        .maxContentLength(1024 * 1024 * 20)
        .idleCloseTime(60_000)
        .build();

BootStrapWebServer server = new BootStrapWebServer(serverConfig);
```

全部参数：

| 方法 | 默认值 | 约束 | 作用 |
| --- | --- | --- | --- |
| `maxConnect` | `10000` | 大于 `0` | 最大连接数 |
| `ioThreadNum` | `1` | 大于 `0` | IO 事件线程数 |
| `cpuNum` | 运行时可用处理器数 | 大于 `0` | 选择连接计数方式的参考值 |
| `sslContext` | `null` | 无 | SSL 上下文，`null` 表示不启用 HTTPS |
| `maxContentLength` | `10485760`（10 MB） | 不小于 `1024` | 聚合后的请求体上限，字节 |
| `enableHttp2` | `false` | 无 | 是否启用 HTTP/2 |
| `http2Settings` | 见下文 | 非 `null` | HTTP/2 帧与流参数 |
| `idleCloseTime` | `60000` | 不小于 `0` | 连接空闲回收阈值，毫秒，`0` 表示不启用 |

### 3.1 连接数上限

`maxConnect` 限制同时存在的 TCP 连接数。连接建立时计数加一，超过上限的新连接直接被关闭；连接断开时计数归还。

计数方式按 `ioThreadNum` 与 `cpuNum` 自动选择：单 IO 线程用无锁计数，IO 线程数不超过 CPU 核心数时用 CAS，否则用互斥锁。`cpuNum` 只参与这个选择，不影响线程数量；操作系统报告的核数与实际配额不一致时可以手动指定。

连接上限和调度限流是两回事：一个限制接入的连接，一个限制同时执行的请求。

### 3.2 IO 线程

`ioThreadNum` 是 worker 的事件循环数量，负责读写事件与协议编解码，默认 1 个。业务逻辑跑在虚拟线程上，IO 线程不参与业务计算，常规服务不必调大，高连接数或高吞吐时再加。

### 3.3 请求体上限

`maxContentLength` 是 `HttpObjectAggregator` 的聚合上限，作用于普通请求体和 `multipart/form-data` 上传。请求体超过该值时，聚合器返回 413 并关闭连接，请求不会进入应用。

上传大文件的服务要按实际文件大小调高，同时注意它也是单请求占用的直接内存上限。

### 3.4 连接空闲回收

`idleCloseTime` 是连接空闲回收阈值，默认 60000 毫秒（60 秒）。一条连接持续该时长没有收发数据，框架自动关闭它，防止客户端异常消失后连接与虚拟线程被白白占用。

```java
ServerConfig.builder()
        .idleCloseTime(120_000) // 空闲 2 分钟回收
        .build();
```

设为 `0` 表示不启用空闲回收。WebSocket、SSE 这类长生命周期连接在协议升级或推送启动时自动豁免回收，不会被误杀；只在它们真正空闲时才按阈值处理。

### 3.5 SSL 与 HTTP/2

`sslContext` 为 `null` 时不启用 HTTPS。传入由证书构建的 `SslContext` 即启用：

```java
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.io.File;

SslContext sslContext = SslContextBuilder.forServer(
        new File("server.crt"),
        new File("server.key")
).build();

ServerConfig serverConfig = ServerConfig.builder()
        .sslContext(sslContext)
        .build();
```

启用 SSL 后服务端通道仍是普通 Nio 实现，写出路径不变。

`enableHttp2` 为 `true` 时必须同时提供 `sslContext`，因为 HTTP/2 依赖 ALPN 协商；只开 HTTP/2 不配 SSL，服务器启动时直接抛 `IllegalArgumentException`。`http2Settings` 的默认值是并发流 256、初始窗口 1 MB、最大帧 16 KB、头列表 8 KB。

## 4. 配置生效时机

- `ServerConfig` 在服务器第一次 `start` 时消费：线程组、服务端通道、连接限流器与空闲回收都在此时按配置创建，之后再改配置对象不会影响已启动的服务器。
- `HttpConfig` 在 `HttpServerRuntime.Builder#build()` 时消费：会话回收参数、调度限流的虚拟线程资源都在这时创建。
- 运行时必须在 `BootStrapWebServer.start` 之前注册，启动后注册抛 `IllegalStateException`。
- 服务器关闭时按注册顺序关闭全部运行时，会话回收线程与中间件等资源随之释放。

## 5. 常用组合

**_内网接口服务_**

```java
ServerConfig.builder()
        .ioThreadNum(1)
        .maxConnect(10_000)
        .build();

HttpConfig.builder()
        .sessionMaxNotUseTime(30 * 60 * 1000)
        .build();
```

不开启调度限流，连接数按实际客户端规模设定；会话必须配一个正的过期时间，否则默认值会在回收时清空全部会话。

**_对外公共接口_**

```java
HttpConfig.builder()
        .enableHttpSchedulerLimit(true)
        .httpSchedulerLimitCount(1_000)
        .httpSchedulerLimitCacheThread(2_000)
        .httpSchedulerLimitTimeout(1_000)
        .sessionCheckTime(60_000)
        .sessionMaxNotUseTime(30 * 60 * 1000)
        .build();
```

并发上限按下游能承受的量设定，排队名额不宜过大，超时设短一些，把压力尽早以 429 返回。

**_慢客户端较多的推送服务_**

```java
ServerConfig.builder()
        .idleCloseTime(120_000)
        .build();

HttpConfig.builder()
        .maxInFlightResponses(16)
        .build();
```

空闲回收阈值放宽，给慢客户端留足重连窗口；单连接在途响应上限调大，允许同一连接上更多请求排队而不被断开。

[首页](../README.md) | [快速入门](./quickstart.md) | [定制：参数解析器](./custom-parameter-parser.md)
