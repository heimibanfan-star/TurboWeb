# 响应体系

控制器方法返回什么，框架就按什么写出响应。返回值先经结果转化链变成 Netty 的 `HttpResponse`，再由响应写出器写入连接。本文说明各类返回值的默认行为、字符集与响应头的处理方式，以及这条链上可以插入的自定义环节。

## 变更说明

重构前，返回值转化与响应写出集中在 `HttpScheduler` 一个执行体里，支持哪些返回类型是写死的。现在拆成两个可以分别扩展的组件：

- 结果转化：`ResultConverterManager` 把返回值转成 `HttpResponse`。六个内置转化器按固定顺序认领，之后是自定义转化器，最后由 JSON 转化器兜底。自定义转化器经 `HttpRequestApplication.Builder#resultConverters(...)` 注册。
- 响应写出：`ResponseWriterManager` 按响应声明的写出器类型选择写出器。响应实现 `ResponseCall` 时，按其 `getWriterType()` 先查内置表、再查自定义表，都不命中则落到 `DefaultResponseWriter`。自定义写出器经 `responseWriters(...)` 注册。

另外两处变化：

- 字符集处理集中到转化出口。文本响应 Content-Type 中缺失的 charset 在这里补写，文件响应在这里编码文件名，`HttpInfoResponse` 暂存的字符串正文也在这里落体。响应编码恒取 `HttpConfig` 的缺省字符集，不受请求头声明影响。
- `ReactorResponse` 不再带泛型参数，构造器直接接收 `Publisher<ByteBuf>`。返回 `Publisher` 时由框架完成元素编码并包装成 `ReactorResponse`。

## 1. 返回值与响应的对应关系

| 返回值 | 状态码 | Content-Type | 说明 |
| --- | --- | --- | --- |
| `String` | 200 | `text/html;charset=<缺省字符集>` | 正文按缺省字符集编码 |
| `HttpResult` | 结果自带 | `text/html` 或 `application/json` | 见第 4 节 |
| `HttpFileResult` | 200 | 声明的类型，缺省 `application/octet-stream` | 见第 7 节 |
| `HttpResponse` | 结果自带 | 结果自带 | 原样写出，不重新包装 |
| `null` | 200 | `text/html;charset=<缺省字符集>` | 响应体为空 |
| `Publisher<?>` | 200 | `text/html;charset=<缺省字符集>` | chunked 流式写出 |
| 其他对象 | 200 | `application/json;charset=<缺省字符集>` | 经 `JsonSerializer` 序列化 |

转化顺序固定：六个内置转化器 → 自定义转化器（按注册顺序）→ JSON 兜底。内置环节先咨询，所以自定义转化器接管不了上表这七类返回值（JSON 兜底同样算内置）。要改变某个自定义类型的默认输出，注册一个认领它的转化器即可，写法见[定制：响应写出策略](./custom-response-writing.md)。

> 返回 `byte[]` 不在内置名单里，会落到 JSON 兜底，被序列化成 Base64 字符串。要输出原始字节，用 `HttpFileResult.bytes(...)` 或自行构造 `HttpResponse`。

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

字符串按 HTML 处理，不做转义：返回值里写什么就发什么。需要 JSON 时返回对象或用 `HttpResult`。字符集取 `HttpConfig` 的缺省值，默认 UTF-8。

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
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .jsonSerializer(new JacksonJsonSerializer())
        .build();
```

Jackson 的默认配置与自定义实现写法见[定制：序列化与实体校验](./custom-serializer-validation.md)。

## 4. HttpResult

`HttpResult<T>` 用于设置状态码和响应头，正文规则与直接返回相同：`String` 按 `text/html`，其他对象按 JSON。

```java
@Get("/user/detail")
public HttpResult<User> detail() {
    HttpHeaders headers = new DefaultHttpHeaders();
    headers.set("X-App", "demo");
    return HttpResult.create(200, headers, new User("张三", 18));
}
```

| 工厂方法 | 状态码 |
| --- | --- |
| `HttpResult.ok(data)` / `ok()` | 200 |
| `HttpResult.err(data)` / `err()` | 500 |
| `HttpResult.create(status, data)` | 指定 |
| `HttpResult.create(status, headers, data)` | 指定 |

响应头的处理顺序是：调用方传入的头先整体写入，随后框架按实际正文覆盖 `Content-Type` 与 `Content-Length`。因此传入头里的同名项不生效，其余头原样保留。

空内容要注意：

```java
return HttpResult.create(204, null); // 正文是字面量 null，Content-Type 为 application/json
```

`HttpResult` 把非字符串的 `data` 一律交给序列化器，Jackson 对 `null` 的序列化结果是字符串 `null`。要返回真正的空正文，用 `new HttpInfoResponse(HttpResponseStatus.NO_CONTENT)` 或返回空字符串。

## 5. 直接返回 HttpResponse

任何 `HttpResponse` 返回后原样写出，不经过序列化。框架提供 `HttpInfoResponse` 作为便捷的全响应对象：

```java
@Get("/resp")
public HttpResponse resp() {
    HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
    response.setContent("hello world");
    response.setContentType("text/plain");
    return response;
}
```

```bash
curl -i http://localhost:8080/resp
# HTTP/1.1 200 OK
# Content-Type: text/plain;charset=UTF-8
# Content-Length: 11
#
# hello world
```

`setContentType("text/plain")` 没写 charset，转化出口检测到它是文本类型且缺少 charset，补成 `text/plain;charset=UTF-8`。显式写了 `text/plain;charset=GBK` 时框架不覆盖。

`HttpInfoResponse` 的内容有三种写法：

- `setContent(String)`：只暂存原文，写出前按缺省字符集编码。适合先取内容、后定响应头与 Cookie 的场景。
- `setContent(String, Charset)`：立即按指定字符集编码。
- `setContent(byte[])`：直接写字节，不涉及字符集。

`setCookie(key, value)` 用于追加 `Set-Cookie`；调用多次会得到多个同名头，不会互相覆盖。

```java
response.setCookie("token", "abc");
response.setCookie("theme", "dark");
```

Netty 的 `DefaultFullHttpResponse` 等普通响应对象同样可以直接返回。它们不实现 `ResponseCall`，由 `DefaultResponseWriter` 写出，缺少 `Content-Length` 时自动补上。

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

框架订阅这个流，逐元素编码成 `ByteBuf`，以 chunked 方式推送。元素编码规则：

| 元素类型 | 编码方式 |
| --- | --- |
| `String` | 按缺省字符集取字节 |
| `Number` | `toString()` 后按缺省字符集取字节 |
| `byte[]` / `ByteBuffer` / `ByteBuf` | 原样写出 |
| 其他对象 | 经 `JsonSerializer` 序列化后按缺省字符集取字节 |

元素之间没有分隔符，需要时自己拼进内容。写出过程有背压：每写出一块，才向上游请求下一块。

要换内容类型，自己构造 `ReactorResponse`：

```java
@Get("/stream/ndjson")
public ReactorResponse lines() {
    Flux<ByteBuf> body = Flux.just("张三", "李四")
            .map(name -> Unpooled.copiedBuffer(name + "\n", StandardCharsets.UTF_8));
    return new ReactorResponse(body,
            ContentType.create("application/x-ndjson", StandardCharsets.UTF_8));
}
```

`ReactorResponse` 的构造参数是已经编好码的 `ByteBuf` 流，框架不再加工元素。`ContentType` 带 charset 时 Content-Type 直接写明，不带时留给转化出口补写。流式写出要求当前线程是虚拟线程，控制器的执行本来就在虚拟线程上；换到平台线程自行写出时，写出器会抛 `IllegalStateException`。

## 7. 文件响应

小文件或内存数据用 `HttpFileResult`：

```java
@Get("/download")
public HttpFileResult download() {
    return HttpFileResult.file(new File("files/manual.pdf"));
}
```

```java
HttpFileResult.file(File file);
HttpFileResult.file(File file, String contentType, boolean openFile);

HttpFileResult.bytes(byte[] bytes);
HttpFileResult.bytes(byte[] bytes, String filename);
HttpFileResult.bytes(byte[] bytes, String filename, String contentType, boolean openFile);

HttpFileResult.png(byte[] bytes);    // jpeg、gif、mp4 用法相同
```

`openFile` 为 `false` 时 `Content-Disposition` 是 `attachment`，为 `true` 时是 `inline`。`png`、`jpeg`、`gif`、`mp4` 四个工厂固定 `openFile = true`，供浏览器直接展示。文件名按缺省字符集做 URL 编码，空文件名会生成随机 UUID。

磁盘文件超过 32MB 时，`HttpFileResult` 在转化环节自动改用 `FileStreamResponse` 分块写出，不会把整个文件读进内存。`closeLimit()` 取消这个阈值，此后无论多大都读入内存，只在确认文件很小时使用。

大文件也可以直接返回三种流式响应：

```java
return new FileStreamResponse(file);   // 分块流式，默认每块 2MB
return new AsyncFileResponse(file);    // 操作系统 AIO，默认每块 8KB
return new ZeroCopyResponse(file);     // 零拷贝，适合大文件
```

三者都只支持磁盘文件，构造时校验文件存在、不是目录、可读，不满足抛 `ResponseHandleException`。`FileStreamResponse` 与 `AsyncFileResponse` 都可以传入分块大小覆盖默认值。

## 8. SSE 响应

SSE 的推送统一使用 `SseEmitter`，由框架装配：作为控制器方法参数声明，框架按当前请求注入；作为返回值返回后，框架写出响应头并开始推送。

`SseEmitter` 自带各类消息的拼装：

```java
@Get("/sse")
public SseEmitter sse(SseEmitter emitter) {
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 10; i++) {
            emitter.sendData("tick " + i);
        }
        emitter.close();
    });
    return emitter;
}
```

`sendData` 自动补 `data:` 与结尾空行，`sendEvent`、`sendId`、`sendRetry`、`sendComment` 同理。连接初始化之前发出的消息进入缓存，初始化后按顺序补发；缓存上限 32 条，溢出抛 `SseBufferException`。`onClose(...)` 注册连接关闭回调。

SSE 的响应头由框架固定为 `text/event-stream`、`no-cache`、`keep-alive`、chunked，charset 由转化出口补写。

## 9. 响应头与字符集

一次响应的加工次序：

1. 转化链按返回类型生成响应，写入 `Content-Type`、`Content-Length`。
2. 转化出口统一善后：补写文本类型缺失的 charset、编码文件响应的文件名、让 `HttpInfoResponse` 暂存的正文落体。
3. 编排器为正常执行链产出的响应追加 `Set-Cookie`，包括 `ctx.cookies()` 里的 Cookie 与新建或路径变更的会话 `JSESSIONID`。
4. 编排器按 `HttpCorsConfig` 写 CORS 响应头，错误响应与 OPTIONS 响应同样处理。
5. 写出器把响应写入连接。`DefaultResponseWriter` 在 `Content-Length` 缺失时补上，值取可读字节数。

字符集规则：

| 场景 | 处理 |
| --- | --- |
| `String`、`Publisher`、`HttpResult(String)` | 正文按缺省字符集编码，Content-Type 直接写明 charset |
| `HttpResult(对象)`、JSON 兜底 | 同上，类型为 `application/json` |
| 自行构造的 `HttpResponse`、自定义转化器的产出 | Content-Type 为 `text/*` 或 `application/json` 且没有 charset 时补写；其他类型不动 |
| 文件响应 | 文件名按缺省字符集 URL 编码；Content-Type 通常是二进制类型，不补 charset |

缺省字符集在 `HttpConfig` 中配置：

```java
HttpConfig config = HttpConfig.builder()
        .defaultCharset(StandardCharsets.UTF_8)
        .build();

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .config(config)
        .build();
```

响应正文的编码恒取这个配置，与请求头声明无关：`Accept-Charset` 不参与，请求体声明的 charset 只用于请求侧解码。

OPTIONS 请求由框架直接返回 200 空响应，不进入路由匹配，也不经过中间件，但仍会写 CORS 头。CORS 各项默认值与白名单的写法见[框架参数调整](./config.md)。

## 10. 自行写出响应

内置响应都不合适时，返回 `IgnoredHttpResponse.ignore()`。对应的写出器不写任何东西，控制器自己向通道写数据：

```java
@Get("/raw")
public HttpResponse raw(HttpContext context) {
    FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.copiedBuffer("raw", StandardCharsets.UTF_8));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
    InternalHttpConnection session = (InternalHttpConnection) context.getConnectSession();
    session.getChannel().writeAndFlush(response);
    return IgnoredHttpResponse.ignore();
}
```

自行写出的数据不经过结果转化和写出器，因此：

- 字符集补写、`Content-Length` 补写都不会发生，响应头要自己写全；
- 框架追加的 CORS 头与会话 Cookie 不会出现在这份响应里；
- 连接的保持、分块编码与关闭由自己负责。

这条路径只在确有需要时使用，普通响应优先用第 5 节的方式构造。

## 11. 出错时的响应

- 路径没有对应的处理器，或路径存在但没有该请求方式的处理器：返回 404，响应体是框架生成的 HTML 错误页。
- 参数绑定、请求体解析、控制器方法抛出的异常没有对应处理器：返回 500，同样带错误页，并记录错误日志。
- 注册了异常处理器时，处理器的返回值走同一条转化链：返回 `User` 得到 JSON，返回 `HttpResult` 得到对应状态码，返回 `HttpResponse` 原样写出。

CORS 头对错误响应同样生效。异常处理器的注册与匹配规则见[异常处理器](./exceptionhandler.md)。

## 12. 扩展点

结果转化与响应写出两段都开放自定义：实现 `ResultConverter` 可以接管新的返回类型，实现 `ResponseWriter` 可以自行控制写出过程。注册入口分别是 `HttpRequestApplication.Builder#resultConverters(...)` 与 `responseWriters(...)`，都要在 `build()` 之前完成。完整的实现要求、判重规则与示例见[定制：响应写出策略](./custom-response-writing.md)。

[首页](../README.md) | [请求体系](./request.md)
