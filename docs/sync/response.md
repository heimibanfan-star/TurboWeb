# <img src="../image/logo.png"/>

# 数据响应的处理

在TurboWeb中将数据返回给客户端有两种方式：一种是基于 ``HttpContext`` 的一些API进行响应；另一种方式是直接基于返回值进行数据的响应（推荐使用返回值进行数据的响应）。

```java
@RequestPath("/user")
public class UserController {}
```

下面的例子都在此Controller中书写。

## HttpContext对响应的处理

### 以字符串的形式响应

```java
@Get("/example01")
public void example01(HttpContext c) {
    c.text("Hello TurboWeb");
}
```

发送请求：

```http
GET http://localhost:8080/user/example01
```

``HttpContext`` 的 ``text()`` 方法是会将字符串以 ``text/plain`` 的方式进行响应。

``text()`` 方法还有另一个重载版本:

```java
c.text(HttpResponseStatus.OK, "Hello TurboWeb");
```

参数1：``HttpResponseStatus`` 响应的状态码。

参数2：响应的具体内容。

### 以HTML的方式进行响应

```java
@Get("/example03")
public void example03(HttpContext c) {
    c.html("<h1>Hello TurboWeb</h1>");
}
```

发送请求：

```http
GET http://localhost:8080/user/example03
```

``HttpContext`` 的 ``html()`` 方法会内容以 ``text/html`` 的方式响应给客户端。

``html()`` 也有另一个重载版本：

```java
c.html(HttpResponseStatus.OK, "<h1>Hello TurboWeb</h1>");
```

参数1：``HttpResponseStatus`` 响应的状态码。

参数2：响应的内容。

### 以JSON的方式进行响应

```java
@Get("/example05")
public void example05(HttpContext c) {
    Map<String, String> userInfo = Map.of(
        "name", "TurboWeb",
         "age", "18"
    );
    c.json(userInfo);
}
```

发送请求：

```http
GET http://localhost:8080/user/example05
```

``HttpContext`` 的 ``json()`` 方法会自动使用 ``ObjectMapper`` 将对象序列化为JSON字符串，之后以 ``application/json`` 的格式将内容响应给客户端。

``json()`` 方法也有另一个重载的方法。

```java
c.json(HttpResponseStatus.OK, userInfo);
```

参数1：``HttpResponseStatus`` 响应的状态码。

参数2：响应的数据对象。

## 使用返回值的方式进行响应的处理

### 以字符串的形式响应

```java
@Get("/example07")
public String example07(HttpContext c) {
    return "Hello TurboWeb";
}
```

发送请求：

```http
GET http://localhost:8080/user/example07
```

对于路由方法来说，如果返回值是 ``String`` 类型，那么TurboWeb会自动将返回值的内容以 ``text/plain`` 的格式响应给客户端。

### 以JSON的方式进行响应

```java
@Get("/example08")
public Map<?, ?> example08(HttpContext c) {
    return Map.of(
        "name", "TurboWeb",
        "age", "18"
    );
}
```

发送请求：

```http
GET http://localhost:8080/user/example08
```

如果是非 ``String`` 类型，并且也不是 ``HttpResponse`` 类型的对象，那么TurboWeb就会自动使用 ``ObjectMapper`` 将返回值的内容序列化为JSON字符串，然后以 ``application/json`` 的格式响应给客户端。

### 响应HttpResponse对象

有的时候 ``text/plain`` 的格式和 ``application/json`` 的格式可能不能满足我们的需求，例如：使用返回值的方式无法直接响应html格式的内容，因此TurboWeb允许开发者自定义 ``HttpResponse`` 类型的对象进行数据的响应。

这里就以响应html格式的内容为例：

```java
@Get("/example09")
public HttpResponse example09(HttpContext c) {
    HttpInfoResponse response = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setContent("<h1>Hello TurboWeb</h1>");
    response.setContentType("text/html");
    return response;
}
```

发送请求：

```http
GET http://localhost:8080/user/example09
```

``HttpInfoResponse`` 是TurboWeb提供的一个更加简化的API对象，内部继承了Netty的 ``DefaultFullHttpResponse`` ，因此也实现了``HttpResponse`` 接口。

构造器的参数：

- 参数1：HTTP协议的版本，这里采用的是HTTP 1.1。
- 参数2：``HttpResponseStatus`` 响应的状态码。

``HttpInfoResponse`` 还提供了另一个重载的构造器，方法签名如下：

```java
public HttpInfoResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content)
```

这里的第三个参数就是响应体的内容。

> 注意：
>
> TuroWeb的 ``HttpInfoRequest`` 没有继承Netty的接口或实现类，是一个单独提供的类。

### 总结一下

对于TurboWeb的返回值方式响应的处理规则如下：

- 返回值是 ``String`` 类型：会直接以 ``text/plain`` 格式进行响应。
- 返回值是 ``HttpResonse`` 类型：会自动以该响应对象定义的内容进行响应。
- 其它类型：如果不是上述的两种类型，TurboWeb会认为该返回值是一个实体对象，会自动使用 ``ObjectMapper`` 将返回值进行序列化，并且以 ``application/json`` 的格式将内容响应给客户端。

## 选择HttpContext的API响应 OR 返回值方式响应？

由于TurboWeb同时支持使用 ``HttpContext`` 的API进行响应和返回值的方式进行响应，那么该如何选择？

**推荐：使用返回值方式进行响应**

在TurboWeb中，**优先推荐使用返回值方式**来响应内容，例如：

```java
@Get("/example07")
public String example07(HttpContext c) {
    return "Hello TurboWeb";
}
```

**注意事项：响应方式只能选择其中一种**

如果你在方法中主动调用了 `text(...)`、`json(...)` 等方法，**框架将直接采用 `HttpContext` 中写入的响应为最终结果，忽略方法返回值**。

即：**`HttpContext` 的写入优先级高于返回值**。

如下列的代码：

```java
@Get("/example10")
public String example10(HttpContext c) {
    c.text("Hello TurboWeb HttpContext");
    return "Hello TurboWeb Return";
}
```

发送请求：

```http
GET http://localhost:8080/user/example10
```

可以看到响应内容如下：

```text
Hello TurboWeb HttpContext
```

**混合使用是不允许的！**

你不能在一个方法中同时使用返回值和 `HttpContext` 的响应写入，两者只能选其一，否则框架会默认使用 `HttpContext`，并忽略返回值。

> 注意：
>
> ``HttpContext`` 中响应数据的API不仅仅如上三种，只要方法签名中标注了``@SyncOnce`` 注解的，那么这些方法都会直接对 ``HttpContext`` 进行内容的写入，对响应的影响和 ``text()`` 、``json()`` 方法类似。



[目录](./guide.md) [请求数据的处理](./request.md) 上一节 下一节 [路由的支持](./router.md)
