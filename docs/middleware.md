# 中间件的使用

中间件是 TurboWeb 处理 HTTP 请求的核心组件，承担着请求流程中的关键处理逻辑。例如之前使用的路由管理器，本质就是一个中间件，负责将请求分发到对应的 Controller 方法。通过扩展中间件，用户可以轻松实现鉴权、限流、日志记录等功能。

## 中间件的基本使用

中间件的作用类似于 Controller，但不同的是，中间件不绑定具体 URL 路径，**所有请求都会经过已注册的中间件**。

**_定义中间件_**

中间件需继承 `Middleware` 类，并实现 `invoke` 方法（核心处理逻辑）：

```java
public class OneMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        // 处理逻辑：如返回固定响应
        return "Hello World";
    }
}
```

**_注册中间件_**

通过 `middleware(..)` 方法注册中间件，示例：

```java
BootStrapTurboWebServer.create()
        .http()
        .middleware(new OneMiddleware())  // 注册中间件
        .and()
        .start();  // 启动服务器
```

注册后，访问服务器的任何路径，都会返回 `Hello World`。

**_核心规则_**

中间件的返回值处理逻辑与 Controller 一致（支持返回`String`、`Map`、实体类等，自动序列化为响应内容）。

若需传递请求到后续组件，需调用 `next(ctx)` 方法（详见下文 “中间件链”）。

## 中间件链与执行顺序

中间件的核心价值在于形成**责任链（Chain）**：每个中间件可对请求 / 响应进行加工、校验，并决定是否将请求传递给下一个中间件。

**_链式执行示例_**

定义两个中间件，演示执行顺序：

```java
// 第一个中间件
public class OneMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("one pre");  // 前置处理
        Object result = next(ctx);      // 调用下一个中间件
        System.out.println("one after"); // 后置处理（下一个中间件返回后执行）
        return result;                  // 返回最终结果
    }
}

// 第二个中间件
public class TwoMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("two");      // 当前中间件处理逻辑
        return "Two Middleware";        // 返回响应内容
    }
}
```

按顺序注册中间件：

```java
BootStrapTurboWebServer.create()
        .http()
        .middleware(new OneMiddleware())  // 先注册第一个
        .middleware(new TwoMiddleware())  // 再注册第二个
        .and()
        .start();
```

访问任意路径，控制台输出：

```text
one pre   // OneMiddleware前置处理
two       // TwoMiddleware处理
one after // OneMiddleware后置处理
```

**_关键结论_**

**执行顺序**：严格按照注册顺序执行，先注册的中间件先执行 “前置逻辑”，后执行 “后置逻辑”（类似栈的 “先进后出”）。

**`next(ctx)` 的作用**：调用下一个中间件，其返回值为下一个中间件的处理结果。若不调用 `next(ctx)`，则请求会在此处终止，后续中间件不再执行。

## 路由管理器也是中间件

之前使用的路由管理器（负责将请求分发到 Controller）本质上是一个特殊的中间件，其核心逻辑如下（简化源码）：

```java
@Override
public Object invoke(HttpContext ctx) {
    // 1. 匹配请求对应的Controller方法
    // ...
    if (routerDefinition == null) {
        // 无匹配路由时抛出异常（最终由异常处理器处理）
        throw new TurboRouterException("router not found: " + ctx.getRequest().getUri());
    }
    // 2. 调用匹配的Controller方法
    try {
        return routerDefinition.invoke(ctx);
    } catch (Throwable e) {
        throw new TurboRouterException("Controller调用失败", e);
    }
}
```

**_注意事项_**

路由管理器需通过 `routerManager(..)` 方法注册，而非 `middleware(..)` 方法。

框架会确保路由管理器作为**最后一个中间件**执行。

## 中间件的典型应用场景：鉴权示例

假设需要限制 `/user` 接口的访问，要求请求头携带 `Authorization: 123456`，否则拒绝访问。

定义鉴权中间件：

```java
public class AuthMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求头中的Authorization
        HttpInfoRequest request = ctx.getRequest();
        String authorization = request.getHeaders().get(HttpHeaderNames.AUTHORIZATION);
        
        // 校验失败：直接返回拒绝信息（终止请求）
        if (!"123456".equals(authorization)) {
            return "lose authorization";
        }
        
        // 校验通过：调用后续中间件（如路由管理器）
        return next(ctx);
    }
}
```

定义 Controller 接口：

```java
@RequestPath("/user")
public class UserController {
    @Get
    public String user(HttpContext context) {
        return "User";  // 鉴权通过后返回的内容
    }
}
```

注册中间件与路由：

```java
// 初始化路由管理器并注册Controller
AnnoRouterManager routerManager = new AnnoRouterManager();
routerManager.addController(new UserController());

// 启动服务器时注册中间件和路由
BootStrapTurboWebServer.create()
        .http()
        .middleware(new AuthMiddleware())  // 先执行鉴权中间件
        .routerManager(routerManager)      // 再执行路由管理器（分发请求）
        .and()
        .start(8080);
```

效果验证：

**无权限访问**：
请求 `GET http://localhost:8080/user`（无 Authorization 头），返回 `lose authorization`。

**有权限访问**：
请求头携带 `Authorization: 123456`，返回 `User`。

## 中间件的初始化方法

中间件提供 `init` 方法，用于在**中间件链组装完成后**执行初始化操作（如加载配置、初始化资源）：

```java
public class MyMiddleware extends Middleware {
    @Override
    public void init(Middleware chain) {
        // 初始化逻辑：如连接数据库、加载黑名单
        System.out.println("中间件初始化完成");
    }
}
```

**_注意事项_**

`init` 方法在所有中间件注册完成、链条锁定前调用，是**最后修改中间件结构的机会**（如动态调整 `next` 指向）。

框架推荐在注册时配置好中间件结构，**不建议在 `init` 中修改链条**，以免破坏执行顺序。

中间件链初始化完成后会被自动锁定，后续调用 `setNext` 方法将失效（防止结构被篡改）



[首页](../README.md) | [异常处理器](./exceptionhandler.md) | [Cookie](./cookie.md)