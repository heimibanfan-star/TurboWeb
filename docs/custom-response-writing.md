# 定制：响应写出策略

控制器的返回值要经过两段加工才变成网络上的一串字节：结果转化器把返回值变成 `HttpResponse`，响应写出器把 `HttpResponse` 写进连接。两段都可以替换或补充，前者决定响应说什么，后者决定响应怎么发。

本文说明这两段的契约、选择规则与注册方式，并给出完整的自定义示例。各类返回值的默认行为见[响应体系](./response.md)。

## 1. 一次响应的加工过程

1. 中间件链返回业务值；
2. `ResultConverterManager` 按转化链把业务值转成 `HttpResponse`；
3. 转化出口做字符集善后：文本类型补写 charset、文件响应编码文件名、`HttpInfoResponse` 的暂存正文落体；
4. 编排器为响应追加 `Set-Cookie`（`ctx.cookies()` 与会话 Cookie），再按 CORS 配置写跨域头；
5. `ResponseWriterManager` 按响应声明的写出器类型选一个写出器，把响应写入连接。

自定义转化器的产出同样经过第 3 步；自定义写出器拿到的响应已经带有 CORS 头，正常链路的响应还带有第 4 步追加的 Cookie。这两点决定了下面两段的边界。

## 2. 结果转化器

### 2.1 契约

```java
public interface ResultConverter {

    enum Status {
        CONTINUE,
        FINISHED
    }

    record Result(Status status, HttpResponse httpResponse) {
        public static Result shouldContinue() { ... }
        public static Result finished(HttpResponse httpResponse) { ... }
    }

    Result convert(Object result, Charset charset);
}
```

每个环节只判断“认不认领”：

- 不认领，返回 `Result.shouldContinue()`，转化链交给下一环节；
- 认领，返回 `Result.finished(response)`，链在此终止，响应进入善后与写出。

`charset` 是 `HttpConfig` 的缺省字符集，正文编码要用它，不要写死 UTF-8。`FINISHED` 必须携带非空响应。

### 2.2 转化链顺序

| 顺序 | 环节 | 认领的返回值 |
| --- | --- | --- |
| 1 | `HttpResultConverter` | `HttpResult` |
| 2 | `StringResultConverter` | `String` |
| 3 | `HttpFileResultConverter` | `HttpFileResult` |
| 4 | `HttpResponseConverter` | 任意 `HttpResponse` |
| 5 | `NullResultConverter` | `null` |
| 6 | `PublisherResultConverter` | `Publisher` |
| 7 | 自定义转化器（按注册顺序） | 自定义返回值 |
| 8 | `JsonResultConverter` | 其余全部，恒认领 |

由此得到三条实际约束：

- 自定义转化器永远接不到前六类返回值（含 `null`）。写一个识别 `String` 的转化器不会被拒登，但它不会被调用。
- 自定义转化器之间按注册顺序咨询，第一个返回 `FINISHED` 的生效。
- 自定义转化器返回 `CONTINUE` 时，最终仍由 JSON 兜底序列化。要改变某个自定义类型的默认输出，必须有人认领它。

异常处理器的返回值走同一条转化链。转化器抛出的异常按异常处理流程处理，处理器返回值会再次进入转化链。

### 2.3 示例

以一个 Markdown 返回值类型为例，输出 `text/markdown`，而不是默认的 JSON。

```java
package demo;

public record Markdown(String text) {
}
```

```java
package demo;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.http.orchestrator.ResultConverter;

import java.nio.charset.Charset;

public final class MarkdownResultConverter implements ResultConverter {

    @Override
    public Result convert(Object result, Charset charset) {
        if (!(result instanceof Markdown markdown)) {
            return Result.shouldContinue();
        }
        byte[] body = markdown.text().getBytes(charset);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/markdown");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, body.length);
        return Result.finished(response);
    }
}
```

控制器：

```java
@Get("/readme")
public Markdown readme() {
    return new Markdown("# TurboWeb\n\n用同步代码写异步应用。");
}
```

注册：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .resultConverters(new MarkdownResultConverter())
        .routeRegister(routeRegister)
        .build();
```

```bash
curl -i http://localhost:8080/readme
# HTTP/1.1 200 OK
# Content-Type: text/markdown;charset=UTF-8
#
# # TurboWeb
#
# 用同步代码写异步应用。
```

转化器只写了 `text/markdown`，charset 由转化出口补上。`text/*` 与 `application/json` 缺 charset 时都会补写，其他媒体类型保持原样。

### 2.4 字符集与善后

转化出口对每个环节的产出做同样的处理：

- `AbstractFileResponse`：按缺省字符集补写 `Content-Disposition` 的文件名编码，已显式写出该头时不覆盖；
- `HttpInfoResponse`：把 `setContent(String)` 暂存的正文按缺省字符集编码落体；
- `Content-Type` 为 `text/*` 或 `application/json` 且没有有效 charset 时，追加 `charset=<缺省字符集>`。

转化器自身要做的是用入参 `charset` 编码正文。响应头里的 charset 可以省略，但不能与正文实际编码不一致。

### 2.5 注册与校验

`HttpRequestApplication.Builder#resultConverters(ResultConverter... resultConverters)` 接收可变参数，按传入顺序进入转化链。

| 情形 | 时机 | 结果 |
| --- | --- | --- |
| 元素为 `null` | 调用注册方法时 | `NullPointerException` |
| 转化器类为七个内置环节之一 | `build()` 装配管理器时 | `IllegalArgumentException` |
| 同一个转化器类注册两次 | `build()` 装配管理器时 | `IllegalStateException` |

`ResultConverter` 是函数式接口，简单转化器可以直接用 lambda 注册，不必单独建类。

自定义转化器由调用方自行构造，应用配置的 `JsonSerializer` 不会自动注入；需要序列化时在构造器里自行传入。

注册只对当前应用生效。`build()` 只能调用一次，构建完成后再改这个建造者不会影响已装配的转化链。

## 3. 响应写出器

### 3.1 选择规则

`ResponseWriterManager` 按响应声明选写出器：

1. 响应实现 `ResponseCall` 时，取 `getWriterType()` 返回的写出器类，先查内置表，再查自定义表；
2. 未实现 `ResponseCall`、或声明的类两张表都没有时，落到 `DefaultResponseWriter`。

匹配按类精确相等。`getWriterType()` 必须返回注册时的那个具体写出器类：返回父类或未登记的类不命中，落到默认写出器；返回 `null` 在选择写出器时抛 `NullPointerException`。

| 内置写出器 | 负责的响应 |
| --- | --- |
| `SseResponseWriter` | `SseResponse`、`SseEmitter` |
| `FileStreamResponseWriter` | `FileStreamResponse` |
| `AsyncFileResponseWriter` | `AsyncFileResponse` |
| `ZeroCopyResponseWriter` | `ZeroCopyResponse` |
| `ReactorResponseWriter` | `ReactorResponse` |
| `IgnoredResponseWriter` | `IgnoredHttpResponse` |
| `DefaultResponseWriter` | 兜底：任意普通 `HttpResponse` |

普通响应不需要自定义写出器。只有发送方式与默认写出不同（分块、异步、零拷贝、自行管理资源生命周期）时，才需要写一个。

### 3.2 契约

```java
public interface ResponseWriter {

    enum WriteType {
        LISTENABLE,
        NOT_LISTENABLE
    }

    record WriteResult(WriteType writeType, ChannelFuture channelFuture) {
        public static WriteResult listenable(ChannelFuture channelFuture) { ... }
        public static WriteResult notListenable() { ... }
    }

    void injectThreadPool(ThreadPool threadPool);

    WriteResult write(HttpResponse httpResponse, InternalConnectSession session);
}
```

可以直接实现该接口，也可以继承 `AbstractResponseWriter` 只实现 `doWrite`。后者已经封装了线程池注入与同步异常处理，推荐使用：

```java
protected abstract ChannelFuture doWrite(HttpResponse response, InternalConnectSession session);
```

写出凭据的含义：

- `doWrite` 返回 `ChannelFuture`：整份响应写完时该凭据完成，`WriteResult` 为可监听，编排器会在它上面挂请求日志回调；
- `doWrite` 返回 `null`：没有可监听的完成凭据，`WriteResult` 为不可监听，请求日志不记录；
- `doWrite` 同步抛出异常：基类记录错误日志、关闭连接，返回不可监听结果。

凭据应当代表“整份响应写完”。返回响应头写出的 future 会让请求日志偏早，返回 `null` 会丢掉完成通知。

异步回调里的异常不在模板方法的捕获范围内。分块写入失败、文件读取失败这类发生在 `doWrite` 返回之后的问题，要由写出器自己失败凭据并关闭连接，参考内置文件写出器的写法。

### 3.3 示例

下面的响应持有逐行数据，由写出器以 chunked 方式发送，写到一行才请求下一行，形成背压。

响应对象：

```java
package demo;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.http.core.response.ResponseCall;
import top.turboweb.http.orchestrator.ResponseWriter;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;

public final class CsvStreamResponse extends DefaultHttpResponse implements ResponseCall {

    private final Charset charset;
    private final Iterator<List<String>> rows;

    public CsvStreamResponse(Charset charset, Iterator<List<String>> rows) {
        super(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        this.charset = charset;
        this.rows = rows;
        headers().set(HttpHeaderNames.CONTENT_TYPE, "text/csv");
        headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
    }

    public Charset charset() {
        return charset;
    }

    public Iterator<List<String>> rows() {
        return rows;
    }

    @Override
    public Class<? extends ResponseWriter> getWriterType() {
        return CsvStreamResponseWriter.class;
    }
}
```

写出器：

```java
package demo;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.orchestrator.AbstractResponseWriter;

import java.util.List;

public final class CsvStreamResponseWriter extends AbstractResponseWriter {

    @Override
    protected ChannelFuture doWrite(HttpResponse response, InternalConnectSession session) {
        if (!(response instanceof CsvStreamResponse csv)) {
            throw new IllegalArgumentException("Invalid response type: " + response.getClass().getName());
        }
        ChannelPromise promise = session.getChannel().newPromise();
        // 先写响应头，头写成功后再逐行发送
        session.getChannel().writeAndFlush(response).addListener(header -> {
            if (header.isSuccess()) {
                writeNextRow(csv, session, promise);
            } else {
                promise.setFailure(header.cause());
                session.close();
            }
        });
        return promise;
    }

    private void writeNextRow(CsvStreamResponse csv, InternalConnectSession session, ChannelPromise promise) {
        if (!csv.rows().hasNext()) {
            session.getChannel().writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, promise);
            return;
        }
        List<String> row = csv.rows().next();
        ByteBuf content = Unpooled.copiedBuffer(String.join(",", row) + "\n", csv.charset());
        session.getChannel().writeAndFlush(new DefaultHttpContent(content)).addListener(written -> {
            if (written.isSuccess()) {
                writeNextRow(csv, session, promise);
            } else {
                promise.setFailure(written.cause());
                session.close();
            }
        });
    }
}
```

控制器：

```java
@Get("/export")
public CsvStreamResponse export(HttpContext ctx) {
    List<List<String>> rows = List.of(
            List.of("orderNo", "amount"),
            List.of("A1001", "99"),
            List.of("A1002", "120"));
    return new CsvStreamResponse(ctx.defaultCharset(), rows.iterator());
}
```

注册：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .responseWriters(new CsvStreamResponseWriter())
        .routeRegister(routeRegister)
        .build();
```

```bash
curl -i http://localhost:8080/export
# HTTP/1.1 200 OK
# Content-Type: text/csv;charset=UTF-8
# Transfer-Encoding: chunked
#
# orderNo,amount
# A1001,99
# A1002,120
```

响应对象的 `Content-Type` 只写了 `text/csv`，charset 由转化出口补写；写出器按 `charset()` 编码每一行，两者一致。

### 3.4 写出时机与线程

写出器实例在应用构建时登记一次，之后所有请求共享同一个实例，实现必须无状态、可并发调用。请求相关的数据放在响应对象里，不要写进写出器字段。

写出发生在处理请求的线程上，正常链路是虚拟线程。向 Netty 通道写入本身是异步的，`doWrite` 里长期阻塞会占住这个虚拟线程；需要读磁盘时，像内置文件写出器那样把任务交给注入的线程池，其他慢操作自行安排执行线程。

`doWrite` 返回后的写出回调跑在通道的事件循环上，回调里同样不能阻塞。示例逐行消费的是内存里的迭代器，可以直接写在回调里；行数据来自磁盘或数据库时，先把取数放到线程池，再回到通道写入。

`AbstractResponseWriter` 的 `threadPool` 字段是构建期注入的磁盘 IO 线程池，同一实例只注入一次。不消费线程池的写出器忽略它即可。同一个写出器实例不能同时装进两个应用，第二次装配会在注入阶段抛 `IllegalStateException`。

`DefaultResponseWriter` 会在 `Content-Length` 缺失时补上：`FullHttpResponse` 取可读字节数，其他响应补 0。自定义写出器走分块编码时不要写 `Content-Length`；写 `Content-Length` 时不要同时声明 chunked。

### 3.5 注册与校验

`HttpRequestApplication.Builder#responseWriters(ResponseWriter... responseWriters)` 接收可变参数。

| 情形 | 时机 | 结果 |
| --- | --- | --- |
| 元素为 `null` | 调用注册方法时 | `NullPointerException` |
| 写出器类为七个内置写出器之一 | `build()` 装配管理器时 | `IllegalArgumentException` |
| 同一个写出器类注册两次 | `build()` 装配管理器时 | `IllegalStateException` |
| 同一实例已在其他应用中装配过（继承 `AbstractResponseWriter` 时） | `build()` 注入线程池时 | `IllegalStateException` |

登记表按写出器类索引，注册顺序不影响选择结果。注册只对当前应用生效，所有调整都要在 `build()` 之前完成。

## 4. 两段如何分工

| 需求 | 做法 |
| --- | --- |
| 换正文格式、状态码或响应头 | 只写结果转化器，产出普通 `HttpResponse`，由默认写出器发送 |
| 换发送方式（分块、异步、零拷贝、资源生命周期） | 自定义响应实现 `ResponseCall`，再注册对应写出器 |
| 正文格式与发送方式都要自定义 | 转化器产出自定义响应类，写出器按这个类写出 |

只在确实需要控制发送过程时才引入写出器。一个只换了 `Content-Type` 的 `FullHttpResponse` 交给默认写出器即可，写一个自定义写出器没有收益。

## 5. 编写约定

- 转化器不认领的返回值必须返回 `shouldContinue()`，不要抛异常。转化链会依次询问每个环节，抛异常会让无关的返回值也失败。
- 转化器实例全应用共享，不要在其中保存请求级状态。
- 正文用入参 `charset` 编码。转化出口只补写响应头里的 charset，不会重新编码正文。
- `FINISHED` 必须带非空响应。需要空正文时用 `HttpInfoResponse` 或内容为空的 `FullHttpResponse`。
- 内置六类返回值与 JSON 兜底不在自定义转化器的可达范围内，不要试图改造它们；要改这些类型的输出，只能改内置实现或换一条返回类型。
- 写出器按 `getWriterType()` 的具体类精确匹配，返回类型要与注册的类一致。
- 写完整份响应再完成凭据。分块响应要发送 `LastHttpContent`，否则客户端会一直等待响应结束。
- 异步阶段的异常由写出器自己收尾：失败凭据并关闭连接，不能让通道停在半份响应上。
- 写出器无状态。请求级数据放进响应对象，不要放进写出器字段。
- 结果转化器经 `resultConverters(...)` 注册，响应写出器经 `responseWriters(...)` 注册，两者都要在 `HttpRequestApplication.Builder#build()` 之前完成。

[首页](../README.md) | [响应体系](./response.md) | [定制：参数解析器](./custom-parameter-parser.md)
