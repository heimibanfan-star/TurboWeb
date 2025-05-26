# <img src="../image/logo.png"/>

# 路由的支持

TurboWeb 的路由系统基于注解驱动，采用**类注解**和**方法注解**相结合的方式来定义 HTTP 路由。通过直观、声明式的方式实现请求路径与控制器方法之间的映射，避免了繁琐的配置文件和手动注册逻辑。

**类注解**（如 `@RequestPath`）用于定义控制器的路径前缀，表示该类下所有方法所共享的路径起始部分。例如：

```java
@RequestPath("/user")
public class UserController {
    ...
}
```

**方法注解**（如 `@GET`、`@POST`、`@PUT`、`@DELETE`）用于声明具体的方法处理某种 HTTP 请求方式及其相对路径。方法路径会与类路径组合形成最终的完整路由路径。例如：

```java
@GET("/list")
public void list(HttpContext ctx) {
    // 处理 GET /user/list 请求
}

@POST("/save")
public void save(HttpContext ctx) {
    // 处理 POST /user/save 请求
}
```

框架在启动的时候会自动根据注解构建高效的路由映射表。TurboWeb 的路由机制区分 HTTP 请求方法，并采用**请求方式+路径双键索引**，实现极快的路由查找性能。

TurboWeb 除了支持基于注解的精确路径匹配外，也支持**路径参数匹配**。你可以通过在路径中使用 `{param}` 的方式声明变量路径段。

当方法路径中存在花括号包裹的变量时（如 `{id}`），该方法将被视为路径路由，匹配以相应段落动态填充的请求路径。例如：

```java
@Get("/{id}")
public String example01(HttpContext c) {
    return "example01";
}
```

此时，请求地址必须包含具体的路径参数：

```http
GET http://localhost:8080/hello/10
```

如果请求的路径未包含参数部分，例如：

```http
GET http://localhost:8080/hello
```

那么会出现如下的错误信息，表示路由匹配不到：

```json
{
  "msg": "未匹配到对应的路由: GET /hello",
  "code": "404"
}
```

**TurboWeb遵循精确路由优先原则**

当一条请求路径同时匹配多个候选路由（如路径参数与精确路径），TurboWeb 始终**优先匹配精确路由**。因为精确路径匹配基于哈希查找，时间复杂度为 `O(1)`，性能优于路径参数匹配。

```java
@Get("/example02/{id}")
public String example02(HttpContext c) {
    return "example02";
}
@Get("/example02/10")
public String example0210(HttpContext c) {
    return "example02 10";
}
```

这个时候发送一个请求：

```http
GET http://localhost:8080/hello/example02/10
```

尽管路径参数 `{id}` 可以匹配 `10`，但由于存在完全一致的精确路径 `/example02/10`，因此系统优先选用精确路由，返回结果为：

```text
example02 10
```

如果将路径参数更换为其他值：

```http
GET http://localhost:8080/hello/example02/1
```

由于没有匹配到精确路径 `/example02/1`，此时会回退至路径参数路由 `/example02/{id}`，最终返回：

```text
example02
```

## TubroWeb的控制器支持的请求方式

TurboWeb的控制器主要支持5中请求方式，分别是 ``GET``、``POST``、``PATCH``、``PUT``、``DELETE``。

### GET请求方式

```java
@Get
public String example03(HttpContext c) {
    return "example03 GET";
}
```

```http
GET http://localhost:8080/hello
```

### POST请求方式

```java
@Post
public String example04(HttpContext c) {
    return "example04 POST";
}
```

```http
POST http://localhost:8080/hello
```

### PATCH请求方式

```java
@Patch
public String example05(HttpContext c) {
    return "example05 PATCH";
}
```

```http
PATCH http://localhost:8080/hello
```

### PUT请求方式

```java
@Put
public String example06(HttpContext c) {
    return "example06 PUT";
}
```

```http
PUT http://localhost:8080/hello
```

### DELETE请求方式

```java
@Delete
public String example07(HttpContext c) {
    return "example07 DELETE";
}
```

```http
DELETE http://localhost:8080/hello
```

## 注意

TurboWeb 在服务启动阶段，会对所有注解声明的路由进行**冲突检测**，防止多重路由声明导致运行时行为不确定。对于无法自动判断或解决的路由冲突，TurboWeb 会在启动时主动抛出 `TurboRouterException` 异常，**中断服务启动**，以确保路由行为的确定性和一致性。

**精确路由冲突**

```java
@Delete
public String example07(HttpContext c) {
    return "example07 DELETE";
}
@Delete
public String example08(HttpContext c) {
    return "example08";
}
```

这两个方法都使用 `@Delete`，默认路径为 `"/hello"`（假设类上加了 `@RequestPath("/hello")`），因此 TurboWeb 无法判断哪个方法应当处理 `DELETE /hello` 请求，最终抛出如下异常：

```text
Exception in thread "main" exception.top.turboweb.commons.TurboRouterException: 路由重复: method:DELETE, path:/hello
	at ...
```

不仅仅如此，路径路由和路径路由之间也会存在路由冲突，只要它们在路径结构上相同，TurboWeb 也会判定为冲突。例如：：

```java
@Get("/err/{id}/{name}")
public String example09(HttpContext c) {
    return "example09";
}
@Get("/err/{name}/{id}")
public String example10(HttpContext c) {
    return "example10";
}
```

TurboWeb 会将路径参数转换为内部统一的正则形式进行匹配，因此上述两个路径形式本质上是**无法区分的路由模板**，同样会导致冲突。

如上的两个路径都会替换为下面的模板进行路由表的构建：

```text
/hello/err/([^/]*)/([^/]*)
```

因此对TurboWeb来说这就是路由冲突了。



[目录](./guide.md) [响应数据的处理](./response.md) 上一节 下一节 [文件的处理](./file.md)
