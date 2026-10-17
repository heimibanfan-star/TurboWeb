# 框架参数调整

TurboWeb 的可调参数分两个对象存放：

- `ServerConfig`：服务器层参数，决定监听线程、连接上限、协议与请求体大小，构造 `BootStrapWebServer` 时传入；
- `HttpConfig`：HTTP 应用层参数，决定请求执行方式、会话回收、调度限流、磁盘 IO 线程池与缺省字符集，构建 `HttpRequestApplication` 时传入。

两个配置都是不可变对象，用建造者组装，链式方法名与参数同名。取值非法时在调用链式方法的那一刻就抛 `IllegalArgumentException`，不会拖到启动时才报错。

Session 管理器、JSON 序列化器、中间件、异常处理器、结果转化器、响应写出器、内容解析器、CORS 属于组件对象，不放在这两个配置里，各有 `HttpRequestApplication.Builder` 上的注册方法。

## 变更说明

- 旧的 `BootStrapTurboWebServer.create().configServer(...)` 已下线。原来集中在 `configServer(..)` 里的参数被拆开：连接与线程相关的进 `ServerConfig`，请求处理相关的进 `HttpConfig`。
- 原“三级限流保护体系”一章已删除。最大连接数仍然是 `ServerConfig.maxConnect`，调度并发限流仍然是 `HttpConfig` 的四个参数，本文按配置项本身说明，不再按“三级”组织。
- 参数对象的建造者在设置时校验，`build()` 只负责打包，不会因为漏配某个参数而失败；唯一的例外是开启调度限流却没有给正数的并发上限，问题会在应用构建时暴露。

## 1. 应用级参数：HttpConfig

```java
import top.turboweb.http.server.application.HttpRequestApplication;
import top.turboweb.http.server.config.HttpConfig;

import java.nio.charset.StandardCharsets;

HttpConfig config = HttpConfig.builder()
        .serializePerConnection(false)
        .sessionCheckTime(60_000)
        .sessionMaxNotUseTime(1_800_000)
        .sessionCheckThreshold(256)
        .enableHttpSchedulerLimit(true)
        .httpSchedulerLimitCount(2_000)
        .httpSchedulerLimitCacheThread(2_000)
        .httpSchedulerLimitTimeout(500)
        .diskOpeThreadCoreQueue(6)
        .diskOpeThreadCacheQueue(4096)
        .diskOpeThreadMaxThreadNum(16)
        .defaultCharset(StandardCharsets.UTF_8)
        .build();

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .config(config)
        .build();
```

全部参数：

| 方法 | 默认值 | 约束 | 作用 |
| --- | --- | --- | --- |
| `serializePerConnection` | `false` | 无 | 同一连接上的请求是否排队执行 |
| `showRequestLog` | `true` | 无 | 请求日志开关，当前版本未接入 |
| `sessionCheckTime` | `300000` | 不小于 `1000` | 会话回收检查间隔，毫秒 |
| `sessionMaxNotUseTime` | `-1` | 无 | 会话最大空闲时长，毫秒 |
| `sessionCheckThreshold` | `256` | 不小于 `1` | 会话数达到该值才执行回收 |
| `enableHttpSchedulerLimit` | `false` | 无 | 是否限制同时执行的请求数 |
| `httpSchedulerLimitCount` | `10000` | 不小于 `0`，开启限流时须大于 `0` | 同时执行的请求上限 |
| `httpSchedulerLimitCacheThread` | `2000` | 不小于 `0` | 超限后允许排队等待的请求数 |
| `httpSchedulerLimitTimeout` | `500` | 不小于 `0` | 排队等待的超时时间，毫秒 |
| `diskOpeThreadCacheQueue` | `4096` | 不小于 `1` | 磁盘 IO 缓冲队列容量 |
| `diskOpeThreadCoreQueue` | `6` | 不小于 `1` | 磁盘 IO 核心队列容量 |
| `diskOpeThreadMaxThreadNum` | CPU 核心数 × 2 | 不小于 `1` | 磁盘 IO 线程上限 |
| `defaultCharset` | `UTF-8` | 非 `null` | 缺省字符集 |

### 1.1 请求串行化

默认关闭，同一条 HTTP/1.1 连接上的多个请求会并发处理，谁先处理完谁先响应。

```java
HttpConfig.builder()
        .serializePerConnection(true)
        .build();
```

开启后每条连接多一个串行处理器：上一条请求的响应写完，才放行下一条。处理期间到达的请求进入连接内的缓冲队列，缓冲上限 8 条，超出时直接关闭该连接。串行化只作用于单条连接，不同连接之间互不影响。

需要严格按请求顺序响应（例如依赖 HTTP/1.1 管线化的客户端）时开启；常规业务保持默认。

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

开启后：

- 许可还有剩余，请求直接执行；
- 许可用完，排队名额未满，请求在虚拟线程里等待，最多等 `httpSchedulerLimitTimeout` 毫秒；
- 等到许可就执行，等不到或者排队名额已满，返回 429，正文为 `too many request`。

`httpSchedulerLimitCount` 是同时执行的数量，不是每秒请求数。开启限流时必须大于 0，否则应用构建失败。排队名额或超时设为 0 时不允许排队，资源不足的请求立刻得到 429。

需要按 QPS 限速时，在中间件里按时间窗口自己计数，这套参数不提供速率控制。

### 1.5 磁盘 IO 线程池

文件流式响应在写出的过程中分块读取磁盘，读取任务跑在这个池里，不占用 Netty 的 IO 线程。

```java
HttpConfig.builder()
        .diskOpeThreadCoreQueue(6)
        .diskOpeThreadCacheQueue(4096)
        .diskOpeThreadMaxThreadNum(16)
        .build();
```

池子按这个顺序扩容：

1. 从 1 个线程起步，任务先进核心队列（`diskOpeThreadCoreQueue`）；
2. 核心队列满了就加线程，直到 `diskOpeThreadMaxThreadNum`；
3. 线程到上限后，任务进缓冲队列（`diskOpeThreadCacheQueue`）；
4. 两个队列都满时拒绝该任务。

核心线程空闲 5 分钟后回收，空闲期间线程数可以回落到 0。大文件下载较多的服务可以适当调大线程上限和缓冲队列；普通服务用默认值即可。

### 1.6 缺省字符集

`defaultCharset` 的默认值是 UTF-8，在四个地方生效：

- 请求体的 `Content-Type` 没有声明 `charset` 时，按它解码；
- 路径参数与查询参数一律按它解码，不看请求头；
- `String`、JSON 等文本响应的正文按它编码；
- Cookie 的值、下载文件的文件名按它编码。

请求头里声明了 `charset` 时，只影响请求体解码，其余三处仍然取缺省字符集。

## 2. 跨域配置：HttpCorsConfig

CORS 由 `HttpCorsConfig` 描述，通过应用建造者的 `corsConfig(...)` 传入：

```java
import top.turboweb.http.orchestrator.HttpCorsConfig;

import java.util.List;

HttpCorsConfig cors = HttpCorsConfig.builder()
        .allowedOrigins(List.of("https://admin.example.com"))
        .allowedMethods(List.of("GET", "POST", "PUT", "DELETE"))
        .allowedHeaders(List.of("Content-Type", "Authorization"))
        .exposedHeaders(List.of("Content-Disposition"))
        .allowCredentials(true)
        .maxAge(600)
        .build();

HttpRequestApplication application = new HttpRequestApplication.Builder()
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
        .zeroCopyThreadNum(8)
        .cpuNum(8)
        .maxContentLength(1024 * 1024 * 20)
        .build();

BootStrapWebServer server = new BootStrapWebServer(serverConfig);
```

全部参数：

| 方法 | 默认值 | 约束 | 作用 |
| --- | --- | --- | --- |
| `maxConnect` | `10000` | 大于 `0` | 最大连接数 |
| `ioThreadNum` | `1` | 大于 `0` | IO 事件线程数 |
| `zeroCopyThreadNum` | CPU 核心数 × 2 | 大于 `0` | 零拷贝传输线程数 |
| `cpuNum` | 运行时可用处理器数 | 大于 `0` | 选择连接计数方式的参考值 |
| `sslContext` | `null` | 无 | SSL 上下文，`null` 表示不启用 HTTPS |
| `maxContentLength` | `10485760`（10 MB） | 不小于 `1024` | 聚合后的请求体上限，字节 |
| `enableHttp2` | `false` | 无 | 是否启用 HTTP/2 |
| `http2Settings` | 见下文 | 非 `null` | HTTP/2 帧与流参数 |

### 3.1 连接数上限

`maxConnect` 限制同时存在的 TCP 连接数。连接建立时计数加一，超过上限的新连接直接被关闭；连接断开时计数归还。

计数方式按 `ioThreadNum` 与 `cpuNum` 自动选择：单 IO 线程用无锁计数，IO 线程数不超过 CPU 核心数时用 CAS，否则用互斥锁。`cpuNum` 只参与这个选择，不影响线程数量；操作系统报告的核数与实际配额不一致时可以手动指定。

连接上限和调度限流是两回事：一个限制接入的连接，一个限制同时执行的请求。

### 3.2 IO 与零拷贝线程

`ioThreadNum` 是 worker 的事件循环数量，负责读写事件与协议编解码，默认 1 个。业务逻辑跑在虚拟线程上，IO 线程不参与业务计算，常规服务不必调大，高连接数或高吞吐时再加。

`zeroCopyThreadNum` 只在未配置 SSL 时生效：此时服务端通道使用零拷贝实现，文件传输等写操作交给这组线程。配置了 SSL 上下文后通道换成普通实现，这组线程不会被创建。

### 3.3 请求体上限

`maxContentLength` 是 `HttpObjectAggregator` 的聚合上限，作用于普通请求体和 `multipart/form-data` 上传。请求体超过该值时，聚合器返回 413 并关闭连接，请求不会进入应用。

上传大文件的服务要按实际文件大小调高，同时注意它也是单请求占用的直接内存上限。

### 3.4 SSL 与 HTTP/2

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

启用 SSL 后服务端通道改用普通 Nio 实现，零拷贝通道与零拷贝线程组都不会创建，文件传输退回普通写出路径。

`enableHttp2` 为 `true` 时必须同时提供 `sslContext`，因为 HTTP/2 依赖 ALPN 协商；只开 HTTP/2 不配 SSL，服务器启动时直接抛 `IllegalArgumentException`。`http2Settings` 的默认值是并发流 256、初始窗口 1 MB、最大帧 16 KB、头列表 8 KB。

## 4. 配置生效时机

- `ServerConfig` 在服务器第一次 `start` 时消费：线程组、服务端通道、连接限流器都在此时按配置创建，之后再改配置对象不会影响已启动的服务器。
- `HttpConfig` 在 `HttpRequestApplication.Builder#build()` 时消费：会话回收参数、调度限流的虚拟线程资源、磁盘 IO 线程池都在这时创建。`Builder#build()` 只能调用一次，重复调用抛 `IllegalStateException`。
- 应用必须在 `BootStrapWebServer.start` 之前注册，启动后注册抛 `IllegalStateException`。
- 服务器关闭时按注册顺序关闭全部应用，应用内部的磁盘 IO 线程池与会话回收线程随之停止。

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

**_大文件下载服务_**

```java
ServerConfig.builder()
        .maxContentLength(1024 * 1024 * 50)
        .zeroCopyThreadNum(16)
        .build();

HttpConfig.builder()
        .diskOpeThreadCoreQueue(16)
        .diskOpeThreadCacheQueue(8192)
        .diskOpeThreadMaxThreadNum(32)
        .build();
```

零拷贝线程与磁盘 IO 线程都服务于文件传输：前者处理通道写出，后者处理分块读取，按磁盘的并发能力一起调整。

[首页](../README.md) | [快速入门](./quickstart.md) | [定制：参数解析器](./custom-parameter-parser.md)
