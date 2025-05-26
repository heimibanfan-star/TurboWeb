# <img src="../image/logo.png"/>

# 中间件的使用

TurboWeb 采用**中间件驱动架构（Middleware-Driven Architecture）** 来处理所有 HTTP 请求。这种架构不仅使得整个框架高度模块化、可插拔，而且为扩展、安全、监控、异常处理等提供了统一、清晰的执行链路。

**核心理念：一切皆中间件**

在 TurboWeb 中，请求经过协议分发器交给调度器之后，后会沿着中间件链依次执行。**每个中间件负责处理一个请求阶段的逻辑，包括但不限于：静态文件、模板技术、流量控制、跨域设置、拦截器、控制器等功能**。

最终的请求分发——即你编写的“控制器方法”调用，本质上也是一个特殊的中间件，它被称为**控制器中间件**，位于中间件链的尾部，用于执行开发者定义的具体业务逻辑。

## 中间件的用法

TurboWeb 中间件机制遵循**洋葱模型（Onion Model）** 的执行顺序，提供了统一、清晰、可组合的请求处理链。开发者可以非常简单地使用和编写中间件，从而对请求和响应流程进行精细控制、扩展或增强。

接下来看一下如何使用中间件。

首先需要定义一个中间件，继承``Middleware`` 抽象类：

```java
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		System.out.println("first 调用下一个中间件之前");
		Object result = next(ctx);
		System.out.println("first 调用下一个中间件之后");
		return result;
	}
}
```

在这里，`next(ctx)` 的调用将控制权交给下一个中间件（由于只添加了一个中间件，因此下一个中间件就是控制器中间件了），并等待其执行完成后再继续执行当前中间件的后置逻辑。

定义一个controller接口打印内容：

```java
@RequestPath("/user")
public class UserController {
	@Get
	public String example01(HttpContext c) {
		System.out.println("controller-user");
		return "example01";
	}
}
```

注册控制器和中间件：

```java
public class MiddlewareApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(MiddlewareApplication.class);
		server.controllers(new UserController());
		server.middlewares(new FirstMiddleware());
		server.start();
	}
}
```

查看控制台的输出：

```text
first 调用下一个中间件之前
controller-user
first 调用下一个中间件之后
```

说明请求按顺序进入中间件链，每一层按“进入→下一层→退出”顺序依次执行。

**中间件的执行模型是什么样的呢？**

```text
┌───────────────────────────────┐
│       FirstMiddleware         │
│   ┌───────────────────────┐  │
│   │    OtherMiddleware     │  │
│   │   ┌───────────────┐   │  │
│   │   │ Controller     │   │  │
│   │   └───────────────┘   │  │
│   └───────────────────────┘  │
└───────────────────────────────┘
```

每个中间件都可以：

- 在调用下一个之前处理“前置逻辑”
- 通过 `next(ctx)` 控制是否继续传递
- 在下一个返回后处理“后置逻辑”
- 中断链（不调用 `next(ctx)`）从而阻止后续处理

**中间件的顺序问题**

中间件的执行顺序是按照中间件的添加顺序来决定的。

我们再定义一个中间件：

```java
public class SecondMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		System.out.println("second 调用下一个中间件之前");
		Object result = next(ctx);
		System.out.println("second 调用下一个中间件之后");
		return result;
	}
}
```

注册中间件：

```java
server.middlewares(new FirstMiddleware(), new SecondMiddleware());
```

发送请求：

```http
GET http://localhost:8080/user
```

查看控制台的打印内容：

```text
first 调用下一个中间件之前
second 调用下一个中间件之前
controller-user
second 调用下一个中间件之后
first 调用下一个中间件之后
```

由于注册中间件的时候 ``FristMiddleware`` 先被添加 ``SecondMiddleware`` 后被添加，因此 ``FristMiddleware`` 优先执行。

这个时候，我们交换一下添加的顺序：

```java
server.middlewares(new SecondMiddleware(), new FirstMiddleware());
```

再次发送相同的请求，查看控制台：

```text
second 调用下一个中间件之前
first 调用下一个中间件之前
controller-user
first 调用下一个中间件之后
second 调用下一个中间件之后
```

顺序正好反了过来，由此可见，在TurboWeb中，中间件的执行顺序是由添加的顺序决定的。

**每个中间件都可以独立完成 HTTP 请求与响应**

什么是“独立完成”？

这意味着一个中间件不仅可以拦截请求、做一些前置处理，还可以：

- 不再继续调用 `next(ctx)`，**直接生成响应**
- **终结请求流程**，阻止后续中间件或控制器的执行
- 中间件可以完成与控制器类似的功能，例如设置响应状态码、构造响应内容等；
- 唯一的区别是：**中间件不参与路由匹配逻辑**，它是在请求进入时统一执行的，而控制器是基于路由的处理器。

这使得中间件在处理 HTTP 请求时具备非常高的灵活性，不仅可以作为拦截器，也可以作为请求的最终响应者。

例如下列的代码：

```java
public class FirstMiddleware extends Middleware {
	@Override
	public Object invoke(HttpContext ctx) {
		return "I am first middleware";
	}
}
```

然后发送任意到达该主机的请求：

```http
GET http://localhost:8080/user
```

可以看到响应内容如下：

```text
I am first middleware
```

由于没有调用 ``next(..)`` 方法，因此后序的中间件不会被执行，请求直接在当前中间件中被处理完成。

需要注意的是，由于在中间件中没有路由的概念，所有的路径都会进入，因此在中间件中**无法获取路径参数**。

TurboWeb 中间件不是简单的“责任链处理器”，而是具备“**主导请求流程**”的能力：

> 你可以把控制器搬到中间件中，也可以让中间件替代控制器。

这赋予了中间件极大的灵活性，使得 TurboWeb 在处理复杂请求流程时更加模块化、高效和优雅。

## TurboWeb内置的中间件

TurboWeb内置了很多的中间件，默认情况下只有控制器中间件会被注册，其余的中间件需要用户手动注册。

### 拦截器中间件

**拦截器中间件是什么？和普通中间件有什么区别？**

初次了解 TurboWeb 时，你可能会有这样的疑问：

> “TurboWeb 中不是已经有中间件机制了吗？那什么是拦截器中间件？”

其实，“拦截器中间件”本质上就是一种具备拦截器能力的特殊中间件，它遵循统一的拦截器接口约定，提供了更结构化的请求生命周期控制。

那么问题来了：

> “既然中间件这么强大，为什么还需要专门定义一个拦截器中间件？”

答案是：**中间件虽然功能灵活，但在处理请求生命周期时粒度较粗，需要开发者通过逻辑自行控制，使用起来相对复杂；而拦截器中间件则专为请求前后处理设计，结构清晰，使用更简单。**

举个例子，假设你希望在请求前做一些准备工作，在请求后统一处理响应结果，并最终做一些清理工作。如果用通用中间件实现，代码可能如下所示：

```java
@Override
public Object invoke(HttpContext ctx) {
    System.out.println("first 调用下一个中间件之前");
    try {
        Object result = next(ctx);
        System.out.println("first 调用下一个中间件之后");
        return result;
    } finally {
        System.out.println("finally");
    }
}
```

虽然可以实现目标功能，但不够直观。比如 `finally` 是和请求后处理并行的，而不是在所有请求后逻辑完成之后才执行。

为了解决这种“结构不清晰、使用不方便”的问题，TurboWeb 提供了 **拦截器中间件（InterceptorMiddleware）**，它通过定义 `preHandler`、`postHandler` 和 `afterCompletion` 三个阶段，清晰划分了请求处理的完整生命周期：

定义拦截器：

```java
public class FirstInterceptor implements HttpInterceptor {
	@Override
	public boolean preHandler(HttpContext ctx) {
		System.out.println("first preHandler");
		return true;
	}
	@Override
	public void postHandler(HttpContext ctx, Object result) {
		System.out.println("first postHandler");
	}
	@Override
	public void afterCompletion(HttpContext ctx, Exception e) {
		System.out.println("first afterCompletion");
	}
}
```

```java
public class SecondInterceptor implements HttpInterceptor {
	@Override
	public boolean preHandler(HttpContext ctx) {
		System.out.println("second interceptor preHandler");
		return true;
	}
	@Override
	public void postHandler(HttpContext ctx, Object result) {
		System.out.println("second interceptor postHandler");
	}
	@Override
	public void afterCompletion(HttpContext ctx, Exception e) {
		System.out.println("second interceptor afterCompletion");
	}
}
```

创建拦截器中间件注册拦截器：

```java
InterceptorMiddleware interceptorMiddleware = new InterceptorMiddleware();
interceptorMiddleware.addLast(new FirstInterceptor());
interceptorMiddleware.addLast(new SecondInterceptor());
server.middlewares(interceptorMiddleware);
```

这个时候访问一下接口：

```http
GET http://localhost:8080/user
```

查看控制台的日志：

```text
first preHandler
second interceptor preHandler
controller-user
second interceptor postHandler
first postHandler
second interceptor afterCompletion
first afterCompletion
```

如你所见，拦截器按照顺序执行 `preHandler`，在请求处理完成后按逆序执行 `postHandler`，最后再按逆序执行 `afterCompletion`。这种结构清晰、职责分明的设计，让你可以方便地管理请求前、请求后和最终处理逻辑。

**拦截器的执行规则是什么呢？**

`InterceptorMiddleware` 是 TurboWeb 中专门用于统一管理拦截器执行流程的中间件。它实现了请求处理的三大核心阶段：**前置处理（preHandler）**、**后置处理（postHandler）**、**完成回调（afterCompletion）**，并提供了灵活的拦截器注册机制。

**拦截器执行流程**

当请求进入 `InterceptorMiddleware` 时，其执行流程如下：

**1.前置处理阶段（preHandler）**

- 所有注册的拦截器依次调用其 `preHandler(HttpContext ctx)` 方法。
- 每个 `preHandler` 返回 `true` 表示继续执行下一个拦截器。
- 一旦某个 `preHandler` 返回 `false`，后续拦截器将不会再被调用，请求中断，不再执行控制器逻辑及 `postHandler`。
- 当前成功执行的拦截器索引被记录，用于后续反向调用。

**2.请求处理阶段**

- 所有 `preHandler` 执行完毕且未中断时，才会执行后续中间件链（即控制器逻辑）。

**3.后置处理阶段（postHandler）**

- 如果控制器正常返回结果，则以**逆序**（从最后一个成功执行的 `preHandler` 开始）依次调用 `postHandler(HttpContext ctx, Object result)`。
- 这一阶段通常用于修改响应、记录日志、收集指标等。

**4.完成回调阶段（afterCompletion）**

- 无论请求是否成功，`finally` 块中都会按 **逆序** 调用 `afterCompletion(HttpContext ctx, Exception e)`，是从最后一个 ``return true`` 的拦截器开始。
- 如果过程中出现异常，异常对象将作为参数传递给每个拦截器。

### 静态资源中间件

TurboWeb提供了静态文件支持的能力，也是通过中间件来实现的。

需要使用TurboWeb对静态文件处理静态文件，首先需要在 `resouces` 目录下创建 `static` 文件夹。

之后在 `static` 文件夹中创建一个静态页面：

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>TurboWeb</title>
</head>
<body>
<h1>TurboWeb</h1>
</body>
</html>
```

在服务器实例中注册静态资源中间件

```java
server.middlewares(new StaticResourceMiddleware());
```

之后访问：

```http
GET http://localhost:8080/static/index.html
```

就可以看到静态资源了。

**那么这个中间件的原理是什么呢？**

静态资源中间件默认会拦截所有以 `/static` 开头的请求，然后根据路径地址从 `resouces/static` 文件夹下寻找静态页面。

例如上面的例子，由于请求是 `/static/index.html` 那么该请求会被静态资源中间件捕获，然后去文件夹下寻找名为 `index.html` 的静态资源。

其实 `StaticResourceMiddleware()` 还有很多可以配置的属性，方法签名如下：

```java
public void setStaticResourceUri(String staticResourceUri)
```

- 配置拦截请求的URL前缀，默认拦截/static开头的URL。

```java
public void setStaticResourcePath(String staticResourcePath)
```

- 配置静态资源存放的文件夹，默认是 `static`。

```java
public void setCacheStaticResource(boolean cacheStaticResource)
```

- 是否开启静态资源缓存，默认开启。

```java
public void setCacheFileSize(int cacheFileSize)
```

- 设置字节大小不超过多少时进行缓存，默认是 `1024 * 1024`。

### 模板引擎中间件

TurboWeb采用了freemarker来处理模板的渲染。

要使用TurboWeb的模板引擎，步骤如下：

首先在 `resouces` 目录下创建 `templates` 文件夹，并创建模板文件 `index.ftl` 。

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>freemarker</title>
</head>
<body>
<h1>${name}</h1>
</body>
</html>
```

创建控制器方法，渲染模板：

```java
@Get("/example02")
public ViewModel example02(HttpContext c) {
    ViewModel model = new ViewModel();
    model.addAttribute("name", "turboweb");
    model.setViewName("index");
    return model;
}
```

TurboWeb的模板中间件会返回值进行拦截，如果返回值是 `ViewModel` 类型，那么模板引擎中间件就会认为需要进行模板渲染。

要使用模板引擎，最重要的一步就是注册模板引擎中间件：

```java
server.middlewares(new FreemarkerTemplateMiddleware());
```

之后请求：

```http
GET http://localhost:8080/user/example02
```

就可以看到模板渲染成功了。

模板引擎中间件也有很多可以配置的属性，方法签名如下：

```java
public void setTemplatePath(String templatePath)
```

- 设置模板文件存储的路径，默认是 `templates` 

```java
public void setTemplateSuffix(String templateSuffix)
```

- 设置模板文件的后缀名，默认是.flt

```java
public void setOpenCache(boolean openCache)
```

- 是否开启模板缓存，默认开启。

### CORS中间件

在开发 Web 应用时，我们经常会遇到**跨域请求失败**的问题。浏览器的同源策略默认禁止不同源的请求（例如：从 `http://localhost:3000` 请求 `http://localhost:8080` 的接口），这时候就需要服务端在响应中显式地添加一些 CORS（Cross-Origin Resource Sharing）相关的 HTTP 头部来允许跨域访问。

你可能会想：这些头能手动加在控制器中不就行了吗？确实可以。但这么做不仅繁琐，还容易遗漏，更无法优雅地应对复杂的跨域场景，例如：

- 只允许某些域名访问；
- 支持带 Cookie 的请求；
- 允许某些方法（如 `PUT`、`DELETE`）；
- 响应预检请求（`OPTIONS`）；

这些逻辑显然不属于业务控制器应该关心的范畴，更适合交给**中间件系统**处理。

于是，**CORS 中间件**就应运而生。

要使用TurboWeb的CORS中间件是非常简单的，直接注册到中间件中即可：

```java
CorsMiddleware corsMiddleware = new CorsMiddleware();
server.middlewares(corsMiddleware);
```

CORS中间件的默认配置如下：

```java
private List<String> allowedOrigins = List.of("*");
private List<String> allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE");
private List<String> allowedHeaders = List.of("*");
private List<String> exposedHeaders = List.of("Content-Disposition");
private boolean allowCredentials = false;
private int maxAge = 3600;
```

如果需要修改，可以调用对应的set方法进行修改。

### 服务信息中间件

有的时候可能需要获取服务器实例运行过程中的一些信息，例如：线程、内存等信息，TurboWeb提供了中间件可以来完成这个功能。

```java
ServerInfoMiddleware serverInfoMiddleware = new ServerInfoMiddleware();
server.middlewares(serverInfoMiddleware);
```

可以通过访问如下地址获取服务信息。

查看内存的信息：

```http
GET http://localhost:8080/turboWeb/serverInfo?type=memory
```

```json
{
  "info": {
    "nio": [
      {
        "name": "mapped",
        "used": 0,
        "capacity": 0
      },
      ......
    ],
  },
  "type": "memory",
  "code": "success"
}
```

查看线程信息：

```http
GET http://localhost:8080/turboWeb/serverInfo?type=thread
```

```json
{
  "info": {
    "stateCount": {
      "TIMED_WAITING": 4,
      "RUNNABLE": 12,
      "WAITING": 6
    },
    "infos": [
      {
        "name": "Attach Listener",
        "id": 12,
        "state": "RUNNABLE",
        "waitedTime": -1,
        "blockedTime": -1,
        "lockName": null
      },
     ......
    ]
  },
  "type": "thread",
  "code": "success"
}
```

查看垃圾回收器信息：

```http
GET http://localhost:8080/turboWeb/serverInfo?type=gc
```

```json
{
  "info": [
    {
      "name": "G1 Young Generation",
      "count": 3,
      "time": 8
    },
    {
      "name": "G1 Concurrent GC",
      "count": 2,
      "time": 1
    },
    {
      "name": "G1 Old Generation",
      "count": 0,
      "time": 0
    }
  ],
  "type": "gc",
  "code": "success"
}
```

该中间件也有一个可配置属性：

```java
public void setRequestPath(String requestPath)
```

- 就是配置服务信息暴露的URL地址，默认是 `/turboWeb/serverInfo`

### 一级限流策略

在高并发场景下，服务的稳定性和可用性往往受到突发流量的强烈冲击。为了保护系统核心资源不被压垮，最直接且有效的方式之一就是**限流**。

TurboWeb 提供了一种开箱即用、简单高效的限流机制——**一级限流策略**。它的核心思想是：

> 不区分请求路径、不区分客户端用户，对所有进入该中间件链的请求统一进行限流。

该策略主要用于控制服务器在某一时刻**允许并发处理的最大请求数**，从而保护后方业务逻辑的稳定运行。

**限流模型与执行行为**

一级限流中间件**并不会阻止请求进入调度器**，而是采用一种**软限流**模型：

> 当系统并发请求数达到设定阈值时，调度器仍会调度该请求，虚拟线程也会被创建并执行中间件链。但在执行到限流中间件时，请求会被识别为“超限”，从而**直接进入拒绝流程**，跳过后续业务逻辑并快速返回响应。

接下来看一下一级限流策略如何使用。

```java
AbstractGlobalConcurrentLimitMiddleware globalConcurrentLimitMiddleware = new AbstractGlobalConcurrentLimitMiddleware(1) {
    @Override
    public Object doAfterReject(HttpContext ctx) {
        return "reject";
    }
};
server.middlewares(globalConcurrentLimitMiddleware);
server.start();
```

`AbstractGlobalConcurrentLimitMiddleware` 是一个抽象类，需要开发者实现`public Object doAfterReject(HttpContext ctx)` 方法，该方法是请求到达阈值之后执行的逻辑，这里直接返回一个字符串，抽象类的构造器参数是设置系统允许的并发请求数，这里设置1个。

接下来我们定义一个控制器方法，让他处理的时间延长一点：

```java
@Get("/example06")
public String example06(HttpContext c) throws InterruptedException {
    Thread.sleep(10000);
    return "example06";
}
```

接下来发送两次请求：

```http
GET http://localhost:8080/user/example06
```

可以发现第一次请求一直处于等待，第二次请求立即返回，并且返回如下内容：

```text
reject
```

这说明第二次请求被立马拒绝了。

> 推荐：
>
> 推荐将一级限流中间件作为第一个中间件。

### 二级限流策略

尽管**一级限流策略**能快速保护系统整体资源，但在实际业务场景中，我们往往需要更细粒度的控制：

- 某些高频接口（如 `/api/search`）比普通页面请求更容易压垮系统；
- 某些敏感资源（如后台管理接口）需要更严格的限流；
- 某些低优先级请求（如埋点、日志上报）应在系统压力大时优先淘汰；

为此，TurboWeb 提供了更精细的限流控制能力——**二级限流策略**。

与一级策略不同，二级限流允许**按 URL 路径前缀划分流量维度**，并为每类前缀配置独立的限流规则。

这使得你可以根据接口的重要性、复杂度或资源消耗情况，灵活分配系统并发处理能力。

> 注意：
>
> 二级限流同样也是软限流，请求都会到达调度创建虚拟线程，只不过是快速走拒绝的逻辑。

二级限流策略配置如下：

```java
AbstractConcurrentLimitMiddleware concurrentLimitMiddleware = new AbstractConcurrentLimitMiddleware() {
    @Override
    public Object doAfterReject(HttpContext ctx) {
        return "second reject";
    }
};
concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/user/example06", 1);
server.middlewares(concurrentLimitMiddleware);
```

`AbstractConcurrentLimitMiddleware` 也是一个抽象类，需要实现 `public Object doAfterReject(HttpContext ctx)` 抽象方法，该方法是请求到达阈值之后执行的逻辑。

`AbstractConcurrentLimitMiddleware` 的 `addStrategy` 的三个参数分别是：

- 参数1：请求方式。
- 参数2：限流的路径前缀。
- 参数3：允许同时并发的数量

例如这里的配置就是限制以GET请求，并且URL以/user/example06开头的请求，同一时刻只能有一个线程并发处理。

接下来定义controller接口测试:

```java
@Get("/example06")
public String example06(HttpContext c) throws InterruptedException {
    Thread.sleep(10000);
    return "example06";
}
@Get("/example07")
public String example07(HttpContext c) throws InterruptedException {
    Thread.sleep(10000);
    return "example07";
}
```

分别对两个接口发送两次请求，发现访问 `example06` 的时候第二次被拒绝，但是 `example07` 可以连续访问。

二级限流中间件可以与一级限流中间件**共同使用**，形成“总量控制 + 精细配额”的双保险：

- 一级限流作为系统总开关；
- 二级限流作为路径级细分策略；

这种组合既能应对突发高并发，又能避免某一类接口资源独占，提升整体系统稳定性与公平性。

如下列的代码：

```java
AbstractGlobalConcurrentLimitMiddleware globalConcurrentLimitMiddleware = new AbstractGlobalConcurrentLimitMiddleware(10) {
    @Override
    public Object doAfterReject(HttpContext ctx) {
        return "reject";
    }
};
AbstractConcurrentLimitMiddleware concurrentLimitMiddleware = new AbstractConcurrentLimitMiddleware() {
    @Override
    public Object doAfterReject(HttpContext ctx) {
        return "second reject";
    }
};
concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/user/example06", 1);
server.middlewares(globalConcurrentLimitMiddleware, concurrentLimitMiddleware);
```

一级限流和二级限流结合使用，推荐将一级限流作为第一个中间件，二级限流作为第二个中间件。



[目录](./guide.md) [异常处理器](./exceptionhandler.md) 上一节 下一节 [Cookie]()