# 响应数据的处理

在 **TurboWeb** 中，处理响应非常简洁，控制器方法直接使用 `return` 即可完成响应。

## 字符串响应

如果希望直接在页面上显示一段文本，例如“Hello World”，只需返回一个字符串：

```java
@Get("/user01")
public String user01(HttpContext context) {
    return "Hello World";
}
```

> **说明：**
>  默认情况下，返回字符串会被当作 `text/html` 类型进行处理。

## 实体对象响应（自动序列化）

当方法返回的是一个普通 Java 实体对象时，TurboWeb 会自动将其序列化为 JSON，响应类型为 `application/json`：

```java
public class User {
    private String name;
    private int age;
    
    // Getter、Setter 省略
}
```

控制器示例：

```java
@Get("/user02")
public User user02(HttpContext context) {
    User user = new User();
    user.setName("张三");
    user.setAge(18);
    return user;
}
```

> **注意：**
>  返回类型为 `HttpResponse`、`HttpResult` 或 `HttpFileResult` 的响应将跳过自动序列化流程，由框架按规则处理。这些类型将在后文详细介绍。

## 使用 `HttpResult` 构建标准响应

当需要设置响应状态码、响应头等信息时，建议使用 `HttpResult`。

**_基本用法_**

```java
@Get("/user03")
public HttpResult<User> user03(HttpContext context) {
    User user = new User();
    user.setName("张三");
    user.setAge(18);
    return HttpResult.ok(user); // 返回 200 OK
}
```

**_状态码控制_**

错误的响应（例如500）：

```java
return HttpResult.err(user);
```

指定任意状态码：

```java
return HttpResult.create(200, user);
```

**_设置响应头_**

```java
HttpHeaders headers = new DefaultHttpHeaders();
headers.set("X-Powered-By", "TurboWeb");
return HttpResult.create(200, headers, user);
```

> **说明：**
>  在 `HttpResult` 中，即使设置了 `Content-Type` 或 `Content-Length`，也不会生效，最终会被 TurboWeb 框架自动覆盖。

## 自定义响应对象

当 `HttpResult` 仍无法满足特定需求时，可以手动构建 `HttpResponse`，实现完全自定义的响应内容。

TurboWeb 提供了对 Netty `DefaultFullHttpResponse` 的封装类 `HttpInfoResponse`：

```java
@Get("/resp")
public HttpResponse resp(HttpContext context) {
    HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
    response.setContent("hello world");
    response.setContentType("text/plain");
    return response;
}
```

> **适用场景：**
>  完全控制响应格式、内容编码、二进制内容处理、非标准响应行为等。

## 流式响应

TurboWeb支持返回Mono或者Flux等响应式流进行流式响应：

```java
@Get("/stream")
public Flux<String> stream(HttpContext context) {
    return Flux.just("hello", "world");
}
```

TurboWeb调度器会自动会改流进行订阅。

TurboWeb对流式响应的支持是依靠 `ReactorResponse` 和对应的策略对象来实现的，默认情况下流式数据响应格式是 `text/html` ,如果用户不满足默认的响应，可以自己构造流式响应对象：

```java
@Get("/stream2")
public ReactorResponse<String> stream2(HttpContext context) {
    Flux<String> flux = Flux.just("hello", "world");
    ReactorResponse<String> response = new ReactorResponse<>(flux);
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    return response;
}
```

## 禁用TurboWeb对响应的处理

如果TurboWeb的内置一系列响应都不满足用户的需求，那么用户可以禁用TurboWeb的响应，自己写出数据。

当用户返回一个 `IgnoredHttpResponse` 的时候，改响应对象到达TurboWeb的 `HttpScheduler` 之后TurboWeb会忽略对改响应的处理，这个时候用户可以手动响应了，如下例子：

```java
@Get("/ignore")
public HttpResponse ignore(HttpContext context) {
    // 创建响应对象
    HttpInfoResponse response = new HttpInfoResponse(HttpResponseStatus.OK);
    response.setContent("hello world");
    response.setContentType("text/plain");
    // 获取连接会话
    InternalConnectSession session = (InternalConnectSession) context.getConnectSession();
    // 发送响应
    session.getChannel().writeAndFlush(response);
    return IgnoredHttpResponse.ignore();
}
```



[首页](../README.md) | [请求数据的处理](./request.md) | [文件的上传和下载](./file.md)

