# 响应体系

控制器方法返回什么，框架就按什么写出响应。返回值先装配为响应消息，再由响应消息写出器按消息类型路由写出到连接。本文说明各类返回值的默认行为、字符集与响应头的处理方式，以及文件、流式与 SSE 三类特殊响应的写法。

## 变更说明

2.6.x 把响应体系整体消息化，旧版的结果转化链与响应写出器不再存在：

- 返回值统一装配为响应消息后按消息类型路由到专用写出处理器。旧的 `ResultConverterManager`、`ResponseWriterManager`、`ResponseCall`、`ReactorResponse`、`HttpFileResult`、`FileStreamResponse`、`HttpInfoResponse` 等类型均已下架，也没有对应的替换类。
- `HttpResult` 重写为非泛型值对象：状态行、响应头实体与原始业务数据三件套，装配规则由框架固定，自定义结果转化器入口不再开放。
- `byte[]` 返回值进入内置名单，原样作为响应体写出；文件响应统一走 `HttpResult.fileOf`。
- 写出环节按 Content-Type 做门禁：未声明内容类型的消息降级写出报错页；文本类声明缺 charset 段时补写缺省字符集，二进制类型不补。
- 同一条连接上的响应按序列定序写出，写出失败自动关闭通道；流式写出带背压控制。

## 1. 返回值与响应的对应关系

| 返回值 | 状态码 | Content-Type | 说明 |
| --- | --- | --- | --- |
| `String` | 200 | `text/html;charset=<缺省字符集>` | 正文按缺省字符集编码 |
| `byte[]` | 200 | `application/octet-stream` | 原样写出，不序列化 |
| `null` | 200 | `text/html;charset=<缺省字符集>` | 响应体为空 |
| `Publisher<?>` | 200 | `text/html;charset=<缺省字符集>` | chunked 流式写出 |
| 其他对象 | 200 | `application/json;charset=<缺省字符集>` | 经 `JsonSerializer` 序列化 |
| `HttpResult` | 结果自带 | 按携带数据装配 | 见第 4 节 |
| 响应消息（`SseEmitter` 等） | 消息自带 | 消息自带 | 原样透传写出，见第 7、8 节 |

装配规则固定在框架内，没有自定义转化器入口。要改变某个类型的输出（加响应头、换状态码、换内容类型），把它包进 `HttpResult` 返回。

## 2. 字符串响应

```java
@Route("/hello")
public class HelloController {

    @Get
    public String hello() {
        return "Hello TurboWeb";
    }
}
```

```bash
curl -i http://localhost:8080/hello
# HTTP/1.1 200 OK
# Content-Type: text/html;charset=UTF-8
# Content-Length: 13
#
# Hello TurboWeb
```

字符串按 HTML 处理，不做转义：返回值里写什么就发什么。需要 JSON 时返回对象或用 `HttpResult`。

## 3. 对象响应

```java
public record User(String name, int age) {
}

@Get("/user")
public User user() {
    return new User("张三", 18);
}
```

```bash
curl -i http://localhost:8080/user
# HTTP/1.1 200 OK
# Content-Type: application/json;charset=UTF-8
#
# {"name":"张三","age":18}
```

对象经 `JsonSerializer` 序列化，默认实现是 `JacksonJsonSerializer`。替换实现：

```java
HttpServerRuntime runtime = HttpServerRuntime.builder()
        .routeRegister(routeRegister)
        .jsonSerializer(new JacksonJsonSerializer())
        .build();
```

Jackson 的默认配置与自定义实现写法见[定制：序列化与实体校验](./custom-serializer-validation.md)。

## 4. HttpResult

`HttpResult` 是响应值对象，承载状态行、响应头与原始业务数据。业务侧需要自定义状态码、响应头或内容类型时返回它，正文装配规则与直接返回相同：`String` 按 `text/html`，`byte[]` 原样，其他对象按 JSON。

```java
@Get("/user/detail")
public HttpResult detail() {
    Headers headers = new Headers();
    headers.set("X-App", "demo");
    return HttpResult.create(200, headers, new User("张三", 18));
}
```

常用工厂：

| 工厂方法 | 状态码 |
| --- | --- |
| `HttpResult.ok(data)` / `ok()` | 200 |
| `HttpResult.err(data)` / `err()` | 500 |
| `HttpResult.create(status, data)` | 指定 |
| `HttpResult.create(status, headers, data)` | 指定 |

响应头是消息自有的 `Headers` 实体（`top.turboweb.http.core.Headers`），构造后可直接读写：

```java
HttpResult result = HttpResult.ok("hello");
result.headers().set("X-Trace-Id", "42");
```

头的处理顺序：调用方声明先整体写入消息；`Content-Type` 只在消息未声明时按数据类型补缺省值，已声明的不覆盖；`Content-Length` 恒由框架按实际响应体算出写入，调用方声明的同名头不生效。其余头原样保留。

空内容直接得到空响应体：

```java
return HttpResult.create(204, null); // 空正文，Content-Type 为 text/html;charset=UTF-8
```

`null` 数据按空体装配，不再交给序列化器。要返回字面量 `null` 的 JSON，返回字符串 `"null"`。

## 5. 文件响应

文件响应统一走 `HttpResult.fileOf`，一行声明字节内容、内容类型与文件名：

```java
@Get("/download")
public HttpResult download() {
    byte[] pdf = loadManual();
    return HttpResult.fileOf(pdf, "application/pdf", false, "manual.pdf");
}
```

| 工厂方法 | 行为 |
| --- | --- |
| `fileOf(byte[] data)` | 附件下载，类型 `application/octet-stream`，随机 UUID 文件名 |
| `fileOf(data, contentType)` | 附件下载，指定内容类型 |
| `fileOf(data, contentType, inline)` | `inline` 为 `true` 时内联展示，`false` 附件下载 |
| `fileOf(data, contentType, inline, filename)` | 指定文件名 |

工厂把内容类型与展示方式装配为 `Content-Type` 与 `Content-Disposition` 头。文件名含非 ASCII 字符或引号、反斜杠时，自动追加 RFC 5987 的 `filename*` 扩展参数（UTF-8 百分号编码），中文文件名在各浏览器下可靠：

```bash
curl -i http://localhost:8080/download
# HTTP/1.1 200 OK
# Content-Type: application/pdf
# Content-Disposition: attachment; filename="manual.pdf"
# Content-Length: 1048576
#
```

文件字节整体持有在内存里，`Content-Length` 由框架按实际字节数补齐。超大文件建议改用第 6 节的流式响应分块产出，避免整份内容驻留堆上。

## 6. 流式响应

返回 `Publisher`（`Flux`、`Mono` 或任意 `org.reactivestreams.Publisher`）即可流式写出：

```java
@Get("/stream")
public Flux<String> stream() {
    return Flux.just("你好", "世界");
}
```

```bash
curl -i http://localhost:8080/stream
# HTTP/1.1 200 OK
# Content-Type: text/html;charset=UTF-8
# Transfer-Encoding: chunked
#
# 你好世界
```

框架订阅这个流，逐元素编码成字节块，以 chunked 方式推送。元素编码规则：

| 元素类型 | 编码方式 |
| --- | --- |
| `String` | 按缺省字符集取字节 |
| `byte[]` | 原样写出 |
| 其他对象 | 经 `JsonSerializer` 序列化后按缺省字符集取字节 |

元素之间没有分隔符，需要时自己拼进内容。写出过程带背压：写出通道来不及消费时上游暂停产出，通道恢复可写后自动续推，慢客户端场景内存占用有上界。背压的完整机制见[背压与事件卸载](./backpressure-offload.md)。

要换内容类型或状态码，把流包进 `HttpResult` 返回：

```java
@Get("/stream/ndjson")
public HttpResult lines() {
    Flux<byte[]> body = Flux.just("张三", "李四")
            .map(name -> (name + "\n").getBytes(StandardCharsets.UTF_8));
    HttpResult result = HttpResult.create(200, body);
    result.headers().set("Content-Type", "application/x-ndjson");
    return result;
}
```

`HttpResult` 携带 `Publisher` 时装配为流式响应消息，状态行与响应头取自值对象，已声明的 `Content-Type` 保留、未声明时补 `text/html;charset=<缺省字符集>`。`Publisher` 里的元素必须是 `String`、`byte[]` 或可 JSON 序列化的对象，元素序列化失败转为流错误信号，写出侧以关闭通道收尾。

## 7. SSE 响应

SSE 的推送统一使用 `SseEmitter`。它本身是一条响应消息，由框架装配：作为控制器方法参数声明，框架按当前请求注入；作为返回值返回后，框架写出响应头并开始推送。

```java
@Get("/sse")
public SseEmitter sse(SseEmitter emitter) {
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            emitter.sendData("tick " + i);
        }
        emitter.complete();
    });
    return emitter;
}
```

`sendData` 等同投递 `data:` 前缀加结尾空行的事件，`sendEvent`、`sendId`、`sendRetry`、`sendComment` 同理，`send` 投递事件原文。写出开始前事件进入内部缓冲，缓冲上限默认 32 条，溢出抛 `SseBufferException`，容量可在构造时指定。`complete()` 发出结束信号，写出侧随后关闭通道；`addCloseListener(...)` 登记连接关闭回调，客户端断开同样触发。

SSE 的响应头由发射器固定为 `text/event-stream`、`no-cache`、`keep-alive`，分块传输声明由写出侧补齐；事件字节编码取 `Content-Type` 的 charset 参数，未声明时降级 UTF-8。SSE 连接不参与空闲回收。

## 8. 直接返回响应消息

响应消息体系下，返回值只要是 `HttpResponseMessage` 的实例就原样透传写出，不经过结果转化。内置的透传形态：

- `SseEmitter`：SSE 推送，见第 7 节；
- `StreamResponseMessage`：以 `Flux<byte[]>` 承载响应体的流式消息，声明由内置流式写出处理器写出；
- `ByteResponseMessage`：以字节数组承载响应体的消息。

自己构造消息时，状态行、响应头与内容类型都要声明齐全；消息未声明 `Content-Type` 时写出侧按门禁降级写出报错页，不再自动补缺省类型。

## 9. 响应头与字符集

一次响应的加工次序：

1. 中间件链与控制器产出返回值，非响应消息的返回值装配为响应消息；
2. 编排器为正常执行链产出的响应追加 `Set-Cookie`，包括 `ctx.cookies()` 里的 Cookie 与新建或路径变更的会话 `JSESSIONID`；
3. 编排器按 `HttpCorsConfig` 写 CORS 响应头，错误响应与 OPTIONS 响应同样处理；
4. 响应消息写出器按消息类型路由写出，同一条连接上的响应按序列定序，写出失败自动关闭通道。

字符集规则：

| 场景 | 处理 |
| --- | --- |
| `String`、`null`、JSON 装配 | 正文按缺省字符集编码，缺省 Content-Type 直接写明 charset |
| 调用方声明的 `Content-Type` 为 `text/*` 或 `application/json` 且缺 charset | 补写缺省字符集 |
| 二进制类型（`application/octet-stream` 及各类文件类型） | 不补 charset |
| SSE 事件编码 | 取 Content-Type 的 charset 参数，未声明降级 UTF-8 |

缺省字符集在 `HttpConfig` 中配置：

```java
HttpConfig config = HttpConfig.builder()
        .defaultCharset(StandardCharsets.UTF_8)
        .build();

HttpServerRuntime runtime = HttpServerRuntime.builder()
        .routeRegister(routeRegister)
        .config(config)
        .build();
```

响应正文的编码恒取这个配置，与请求头声明无关：`Accept-Charset` 不参与，请求体声明的 charset 只用于请求侧解码。

OPTIONS 请求由框架直接返回 200 空响应，不进入路由匹配，也不经过中间件，但仍会写 CORS 头。CORS 各项默认值与白名单的写法见[框架参数调整](./config.md)。

## 10. 出错时的响应

- 路径没有对应的处理器，或路径存在但没有该请求方式的处理器：返回 404，响应体是框架生成的 HTML 错误页。
- 参数绑定、请求体解析、控制器方法抛出的异常没有对应处理器：返回 500，同样带错误页，并记录错误日志。
- 注册了异常处理器时，处理器的返回值按第 1 节的同一套规则装配：返回 `User` 得到 JSON，返回 `HttpResult` 得到对应状态码，返回响应消息原样透传。

CORS 头对错误响应同样生效。异常处理器的注册与匹配规则见[异常处理器](./exceptionhandler.md)。

## 11. 扩展点

写出管线按消息类型路由到写出处理器，处理器契约（`MessageWriteHandler`）与内置管线的门禁、降级、定序行为见[定制：响应写出策略](./custom-response-writing.md)。应用级自定义写出处理器的注册入口当前版本未开放；业务侧影响写出内容的入口是返回值本身——声明 `HttpResult` 的状态行与响应头，或直接返回内置的响应消息类型。

[首页](../README.md) | [请求体系](./request.md)
