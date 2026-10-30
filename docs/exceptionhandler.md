# 异常处理器

控制器、参数绑定、中间件抛出的异常，最终由异常处理器产出响应。处理器是一个普通对象，框架按方法签名从中找出能处理某类异常的方法，把异常实例交给它，再把它的返回值按正常响应写出。

## 变更说明

- 旧版用 `@ExceptionHandler` 指定异常类型、用 `@ExceptionResponseStatus` 指定状态码。两个注解都已去掉：方法只要公有、只有一个形参、且形参类型是 `Throwable` 的子类，就被认领为处理方法，异常类型就是形参类型；状态码由返回的 `HttpResult` 决定。
- 默认错误响应由 JSON 改为 HTML 错误页，500 页面带异常类型与完整异常栈。
- 注册入口由单个实例改为可变参数：`exceptionHandlers(...)` 一次可以传入多个处理器对象。

## 1. 默认行为

没有登记处理器，或者登记的处理器都接不住某个异常时，框架按下面的规则直接产出响应：

| 情形 | 状态码 | 响应体 |
| --- | --- | --- |
| 路径没有对应的处理器，或路径存在但没有该请求方式的处理器 | 404 | 客户端错误页，正文是异常消息 |
| 必填查询参数缺值 | 400 | 同上 |
| 路径或查询参数的百分号转义畸形 | 400 | 同上 |
| 其余异常（参数绑定、请求体解析、控制器方法、中间件等） | 500 | 服务器错误页，含异常类型与完整异常栈，同时打印 ERROR 日志 |

前两行的 4xx 响应来自 `RouteHandlerException` 携带的错误码，页面只有状态码、理由短语与异常消息，不带异常栈：

```bash
curl -i http://localhost:8080/not-exist
# HTTP/1.1 404 Not Found
# Content-Type: text/html;charset=UTF-8
#
# <!DOCTYPE html>
# ...
# <h1>404 Not Found</h1>
# <p class="error-message">Exception message: not found route:GET /not-exist</p>
# ...
```

500 页面展示异常类型与 `printStackTrace` 的完整输出，嵌入页面前做了 HTML 转义。页面只用于定位问题，对外提供的接口应按后面的方式登记处理器，返回自己的错误格式。

## 2. 编写与注册处理器

处理器类的全部公有方法都会被扫描，满足下面三条的方法被认领：

- 方法是公有的；
- 只有一个形参；
- 形参类型是 `Throwable` 的子类。

方法名、返回值类型都不参与判定，异常类型取形参类型。从父类继承来的公有方法同样会被扫描，所以处理器类里不要再放参数是异常类型的普通公有工具方法，它会被当成处理器。

```java
package demo;

import top.turboweb.validation.BeanValidationException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.response.HttpResult;

public class GlobalExceptionHandler {

    public HttpResult<String> validationFailed(BeanValidationException e) {
        return HttpResult.create(400, String.join("; ", e.failMessages()));
    }

    public HttpResult<String> routeNotFound(RouteHandlerException e) {
        return HttpResult.create(e.getCode().getHttpStatus().code(), "route not found");
    }
}
```

在 `build()` 之前登记：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .exceptionHandlers(new GlobalExceptionHandler())
        .build();
```

多个处理器一次传入即可，也可以对同一个 Builder 连续调用 `exceptionHandlers(...)`：

```java
.exceptionHandlers(new ValidationExceptionHandler(), new OrderExceptionHandler())
```

传入 `null` 立即抛 `NullPointerException`；同一个异常类型登记了两个处理方法时，`build()` 抛 `IllegalStateException`，这一步不会拖到运行期。

## 3. 匹配规则

匹配看异常对象的运行时类型，从自身开始沿父类链向上，取第一个登记过的类型。子类处理器优先，与登记顺序无关。

以 `BeanValidationException`（`RuntimeException` 的子类）为例：

| 登记的异常类型 | 是否命中 |
| --- | --- |
| `BeanValidationException` | 命中 |
| `RuntimeException` | 命中（没有登记更具体的类型时） |
| `IllegalArgumentException` | 不命中 |
| `Throwable` | 命中（最后兜底） |

整条父类链上都没有登记时，走第 1 节的默认行为。

路由未匹配、缺少必填参数、百分号转义畸形都抛 `RouteHandlerException`，只是错误码不同。登记 `RouteHandlerException`、`RuntimeException` 或 `Throwable` 处理器后，第 1 节那张表里的 4xx 默认页也会被接管，需要自己按 `e.getCode()` 分流，否则 404 会变成统一格式的响应。

## 4. 处理方法的返回值

处理方法的返回值走与控制器方法相同的转化链，规则见[响应体系](./response.md)。常用的几类：

| 返回值 | 写出结果 |
| --- | --- |
| `String` | 200，`text/html` |
| `HttpResult` | 状态码与响应头按结果 |
| 普通对象 | 200，`application/json` |
| `HttpResponse` | 原样写出 |
| `null`，或方法声明为 `void` | 200，空正文 |

状态码只能经 `HttpResult` 或自行构造的 `HttpResponse` 设置，返回 `String` 或普通对象固定为 200。

```java
public HttpResult<Map<String, Object>> businessError(BusinessException e) {
    return HttpResult.create(409, Map.of("code", e.code(), "message", e.getMessage()));
}
```

处理器自身抛出异常时，框架打印 `Failed to execute exception handler` 日志，然后对原始异常走 500 兜底页，不会因为处理器的错误再次进入处理器。处理器返回值的转化如果失败，也不会二次交给异常处理器。

## 5. 可捕获范围

异常处理器接住的是从请求装配到结果转化这一段流程里抛出的异常，具体包括：

| 阶段 | 是否进入异常处理器 |
| --- | --- |
| 请求体解析（`Content-Type` 不受支持、请求体格式不合法等） | 是 |
| 会话初始化、中间件链 | 是 |
| 路由命中、参数绑定、参数校验 | 是 |
| 控制器方法 | 是 |
| 控制器返回值的结果转化 | 是 |
| OPTIONS 预检 | 否，直接返回 200 空响应 |
| CORS 头写入、响应写出 | 否 |
| 处理器返回值的转化与写出 | 否 |
| 虚拟线程资源耗尽产生的 429 | 否，提交阶段直接写出 |

几点需要知道的副作用：

- 参数校验发生在调用控制器方法之前，控制器里 `try...catch` 捕不到 `BeanValidationException`，只能交给异常处理器。
- 异常响应不追加本次请求设置的 Cookie 与会话 Cookie。Cookie 回写位于正常链路的收尾，异常路径不经过这一段。
- CORS 头对错误响应同样生效，与正常响应一致。
- 连接层异常（例如连接断开）不属于请求处理流程，不经过异常处理器。

## 6. 示例：校验失败返回 400

实体上声明校验规则：

```java
public class UserForm {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Min(value = 1, message = "年龄必须大于 0")
    private Integer age;

    // getter / setter 省略
}
```

`@BeanForm` 的 `value` 打开校验：

```java
@Route("/user")
public class UserController {

    @Post("/create")
    public String create(@BeanForm(true) UserForm form) {
        return "created " + form.getUsername();
    }
}
```

处理器把全部违规消息拼起来，按 400 返回：

```java
public class ValidationExceptionHandler {

    public HttpResult<String> handle(BeanValidationException e) {
        return HttpResult.create(400, String.join("; ", e.failMessages()));
    }
}
```

```bash
curl -i -X POST http://localhost:8080/user/create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "age=0"
# HTTP/1.1 400 Bad Request
# Content-Type: text/html;charset=UTF-8
#
# 用户名不能为空; 年龄必须大于 0
```

`failMessages()` 返回全部违规消息，顺序由校验引擎给出的违规集合决定，不保证与字段声明顺序一致。需要固定顺序或字段名时，遍历 `getViolations()` 自行拼装。

## 7. 示例：业务异常映射为状态码

业务代码抛出自己的异常：

```java
public class OrderNotFoundException extends RuntimeException {

    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("order not found: " + orderId);
        this.orderId = orderId;
    }

    public long orderId() {
        return orderId;
    }
}
```

```java
@Route("/order")
public class OrderController {

    @Get("/:orderId")
    public String detail(@PathParam("orderId") long orderId) {
        if (orderId == 0) {
            throw new OrderNotFoundException(orderId);
        }
        return "order " + orderId;
    }
}
```

处理器返回 JSON：

```java
public class OrderExceptionHandler {

    public HttpResult<Map<String, Object>> orderNotFound(OrderNotFoundException e) {
        return HttpResult.create(404, Map.of(
                "message", e.getMessage(),
                "orderId", e.orderId()));
    }
}
```

```bash
curl -i http://localhost:8080/order/0
# HTTP/1.1 404 Not Found
# Content-Type: application/json;charset=UTF-8
#
# {"message":"order not found: 0","orderId":0}
```

## 8. 兜底处理器

登记一个 `Throwable` 参数的方法，就能接住所有没有被更具体处理器命中的异常：

```java
public class FallbackExceptionHandler {

    public HttpResult<Map<String, String>> fallback(Throwable e) {
        return HttpResult.create(500, Map.of("message", "internal error"));
    }
}
```

需要清楚两点：

- `Throwable` 在父类链的最顶端，登记之后路由未匹配的 404 也会落到它这里。要保留默认的 400、404 页面，必须同时登记更具体的 `RouteHandlerException` 处理器，或者在兜底方法里先判断 `RouteHandlerException` 再决定状态码。
- 兜底方法拿到的是原始异常，需要记录日志的话在这里记。框架自身的 500 日志只在没有处理器命中时才打印。

[首页](../README.md) | [响应体系](./response.md) | [中间件的使用](./middleware.md)
