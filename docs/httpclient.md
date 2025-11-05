# HTTP 客户端

TurboWeb 提供了一个简洁而强大的 **HTTP 客户端**，其使用风格与前端的 **Axios** 十分类似。
通过极简的链式调用方式即可完成 HTTP 请求、参数传递、响应解析与拦截处理。

## 快速开始

首先，创建一个测试服务器，定义一些 API 接口：

```java
manager.addGroup(new LambdaRouterGroup() {
    @Override
    protected void registerRoute(RouterRegister register) {
        register.get("/hello", ctx -> "Hello World");
        register.get("/user", ctx -> new User("Tom", 18));
        register.get("/query", ctx -> ctx.loadQuery(User.class));
        register.post("/json", ctx -> ctx.loadJson(User.class));
        register.post("/form", ctx -> ctx.loadForm(User.class));
        register.get("/interceptor", ctx -> {
            HttpHeaders headers = ctx.getRequest().headers();
            return headers.get("Authorization");
        });
    }
});
```

接下来创建一个客户端来请求这些接口：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
String res = httpClient.get("http://localhost:8080/hello").as(String.class);
System.out.println(res);
```

> ✅ **说明**
>  `DefaultTurboHttpClient()` 无参构造器会创建一个默认的客户端引擎。
>  当请求结束后，结果以 Netty 的 `ByteBuf` 形式返回。
>  调用 `.as(...)` 方法时会自动释放内存；若不调用 `as()`，则需手动 `release()` 释放。

------

## 响应处理

可以将返回结果直接转换为对象类型：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
User user = httpClient.get("http://localhost:8080/user").as(User.class);
System.out.println(user);
```

------

## 发送请求数据

### URL 参数

通过回调设置 URL 查询参数：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
User user = httpClient.get("http://localhost:8080/query", config -> {
    config.query(params -> {
        params.add("name", "Tom");
        params.add("age", "18");
    });
}).as(User.class);
System.out.println(user);
```

### JSON 请求体

当请求方法为 `POST` 或 `PUT` 且传入 data 参数时，会自动设置为 `application/json`：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
User user = new User("Tom", 18);
User result = httpClient.post("http://localhost:8080/json", user).as(User.class);
System.out.println(result);
```

或使用配置回调的通用方式：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
User user = new User("Tom", 18);
User result = httpClient.post("http://localhost:8080/json", config -> {
    config.data(user);
}).as(User.class);
System.out.println(result);
```

> 📘 `config.data()` 会自动写入 `application/json` 请求头。

### 表单参数

通过 `form()` 方法设置表单参数：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
User user = httpClient.post("http://localhost:8080/form", config -> {
    config.form(params -> {
        params.add("name", "Tom");
        params.add("age", "18");
    });
}).as(User.class);
System.out.println(user);
```

### 请求头

通过 `headers()` 方法设置请求头：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
String token = httpClient.get("http://localhost:8080/interceptor", config -> {
    config.headers(headers -> {
        headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer 123456");
    });
}).as(String.class);
System.out.println(token);
```

## 原始字节读取

若只需获取原始字节流，可直接转换为 `byte[]`：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();
byte[] bytes = httpClient.get("http://localhost:8080/hello").as(byte[].class);
System.out.println(new String(bytes));
```

> ⚠️ **注意**
>  此方法会复制 `ByteBuf` 中的内容并自动释放原始内存。
>  若数据量较大，可能影响性能。

------

## 拦截器

与 Axios 类似，TurboWeb 的客户端支持 **请求拦截器** 与 **响应拦截器**：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient();

// 注册请求拦截器
httpClient.addRequestInterceptor(request -> {
    request.headers().add(HttpHeaderNames.AUTHORIZATION, "Bearer 123456");
    return request;
});

// 注册响应拦截器
httpClient.addResponseInterceptor(response -> {
    System.out.println(response.status());
    return response;
});

// 发起请求
String token = httpClient.get("http://localhost:8080/interceptor").as(String.class);
System.out.println(token);
```

------

## 构造器与客户端配置

### 基础 URL

可在构造器中指定公共的基础路径：

```java
TurboHttpClient httpClient = new DefaultTurboHttpClient("http://localhost:8080");
String res = httpClient.get("/hello").as(String.class);
System.out.println(res);
```

### 自定义引擎

底层的所有请求由 `HttpClientEngine` 负责发送。
 默认仅使用一个 IO 线程（足以应对大多数场景）。

```java
HttpClientEngine engine = new HttpClientEngine(1, "http://localhost:8080");
TurboHttpClient httpClient = new DefaultTurboHttpClient(engine);
String res = httpClient.get("/hello").as(String.class);
System.out.println(res);
```

### 自定义转换器

若需自定义数据转换方式，可在构造时传入转换器：

```java
HttpClientEngine engine = new HttpClientEngine(1, "http://localhost:8080");
TurboHttpClient httpClient = new DefaultTurboHttpClient(engine, new JsonConverter());
User user = httpClient.get("/user").as(User.class);
System.out.println(user);
```

默认转换器为 `JSON` 转换器。

## HTTP 客户端引擎配置

TurboWeb 的 HTTP 客户端底层基于 **reactor-netty** 实现，非阻塞 IO，默认单线程。
 也可通过回调方式灵活配置：

```java
File cert = new File("E://temp/server.cert");

HttpClientEngine engine = new HttpClientEngine(config -> {
    config.ioThread(1);                          // IO线程数
    config.baseUrl("https://localhost:8080");   // 基础URL
    config.name("testHttpClient");               // 客户端名称
    config.timeout(5000);                        // 超时时间(ms)

    config.connect(builder -> {
        // Reactor-Netty连接配置
    });

    config.ssl(spec -> {
        try {
            spec.sslContext(SslContextBuilder.forClient().trustManager(cert).build());
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    });
});

TurboHttpClient httpClient = new DefaultTurboHttpClient(engine);
String res = httpClient.get("/hello").as(String.class);
System.out.println(res);
```

> 💡 默认情况下 TurboWeb 使用 JDK 自带的 SSL 实现。
>  仅在使用自签证书时需手动配置 `sslContext`。



[首页](../README.md) | [走向HTTP2.0](./http2.md) | [多版本路由控制](./mvrc.md)