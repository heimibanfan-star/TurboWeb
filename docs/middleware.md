# 中间件的使用

中间件是请求处理链上的环节，用来做鉴权、日志、限流这类与具体接口无关的事。它不绑定路径，所有进入请求处理流程的请求都会依次经过已注册的中间件，最后由链尾的路由节点分发到控制器。

## 变更说明

- 注册入口由 `BootStrapTurboWebServer.create().http().middleware(...)` 改为 `HttpRequestApplication.Builder#middleware(...)`，参数改为可变参数。
- 路由管理器不再作为普通中间件手工注册。设置 `routeRegister` 之后，框架在构建期把路由调用节点追加到链尾；不设置时不追加，链在执行完业务中间件后结束。
- 同一个中间件类只能注册一次，重复注册由构建期检查，`build()` 抛 `IllegalStateException`。
- 请求对象的读取入口由 `ctx.getRequest()` 改为 `ctx.message()`。
- 构建期除 `init` 外新增 `destroy`，应用关闭时统一回调中间件释放资源。

## 1. 编写与注册中间件

继承 `Middleware`，实现 `invoke`。返回值就是本次请求的处理结果，按与控制器的返回值相同的方式转化并写出。

```java
package demo;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

public class HelloMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        return "Hello World";
    }
}
```

在装配应用时注册，`build()` 之前完成：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .middleware(new HelloMiddleware())
        .build();

BootStrapWebServer server = new BootStrapWebServer();
server.registerApplicationLast(application);
server.start(8080);
```

注册后访问任意路径都返回 `Hello World`，控制器不会执行，因为中间件没有把请求交给下一个环节。

`middleware(...)` 接受多个实例，也可以连续调用：

```java
.middleware(new AuthMiddleware())
.middleware(new AccessLogMiddleware(), new MetricsMiddleware())
```

传入 `null` 立即抛 `NullPointerException`。同一个类注册两次会在 `build()` 抛 `IllegalStateException`，即使两个实例的配置不同也一样。

中间件实例在整个应用内只有一份，被所有请求共用，并在各自的虚拟线程上并发执行。实例里不要保存与单次请求相关的数据，计数、缓存这类共享状态要用线程安全的容器。

## 2. 中间件链与执行顺序

构建期按注册顺序把中间件串成一条链，链头是框架内部使用的哨兵节点；挂了路由登记表时，路由调用节点追加在链尾：

```text
SentinelMiddleware -> AuthMiddleware -> AccessLogMiddleware -> RouteHandlerInvokeMiddleware
```

每个中间件通过 `next(ctx)` 调用下一个环节，返回值是后续链路的处理结果：

```java
public class OneMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("one pre");
        Object result = next(ctx);
        System.out.println("one after");
        return result;
    }
}

public class TwoMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        System.out.println("two");
        return "Two Middleware";
    }
}
```

按 `OneMiddleware`、`TwoMiddleware` 的顺序注册，访问任意路径输出：

```text
one pre
two
one after
```

执行顺序就是注册顺序。先注册的中间件先做前置处理，等后面的环节全部返回后再做后置处理。

不调用 `next(ctx)` 时请求在当前中间件终止，后面的中间件和路由都不会执行。调用两次 `next(ctx)` 会让后续链路执行两遍，每个中间件最多调用一次。

链尾没有路由节点时（没有设置 `routeRegister`），`next(ctx)` 返回 `null`，最终得到 200 空响应。

中间件的返回值走结果转化链，规则与控制器一致：返回 `String` 是 200 的 `text/html`，返回普通对象是 JSON，返回 `HttpResult` 按其中的状态码写出，返回 `HttpResponse` 原样写出。详见[响应体系](./response.md)。

## 3. 短路请求

鉴权是最常见的短路场景。校验不通过时直接返回自己的响应，不调用 `next`：

```java
import top.turboweb.http.response.HttpResult;

public class AuthMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        String authorization = ctx.message().header("Authorization");
        if (!"123456".equals(authorization)) {
            return HttpResult.create(401, "unauthorized");
        }
        return next(ctx);
    }
}
```

```bash
curl -i http://localhost:8080/user
# HTTP/1.1 401 Unauthorized
# Content-Type: text/html;charset=UTF-8
#
# unauthorized

curl -i -H "Authorization: 123456" http://localhost:8080/user
# HTTP/1.1 200 OK
#
# User
```

状态码只能经 `HttpResult` 或自行构造的 `HttpResponse` 设置，直接返回字符串固定为 200。

短路响应与正常响应走同一条收尾：CORS 头照常写入；中间件通过 `ctx.cookies()` 设置的 Cookie 与本次新建会话的 `JSESSIONID` 也会写进响应。中间件抛出异常时，异常交给异常处理器，处理方式见[异常处理器](./exceptionhandler.md)。

中间件在路由匹配之前执行，拿不到将要命中的控制器信息，只能根据请求方式、路径、请求头、Cookie 与会话判断。请求体在进入中间件链之前已经解析完成，中间件可以直接读取。

## 4. 执行范围与线程模型

| 情形 | 是否经过中间件 |
| --- | --- |
| 普通 HTTP 请求 | 经过，执行在本次请求的虚拟线程上 |
| OPTIONS 预检 | 不经过，框架直接返回 200 空响应 |
| 虚拟线程资源耗尽被拒（429） | 不经过，提交阶段直接写出 |
| CORS 头写入、Cookie 与会话回写、响应写出 | 不经过中间件，由编排器统一处理 |

中间件是同步代码，运行在虚拟线程上，写阻塞式 I/O（数据库、外部 HTTP 调用）没有问题，虚拟线程会在阻塞时让出载体线程。不要为了“异步”把逻辑另起线程后立即返回，那样请求的结果与响应写出会脱节。

## 5. 初始化、锁定与销毁

`init(Middleware chain)` 在链装配完成之后、锁定之前调用，每个中间件调用一次，用来创建线程池、加载配置等长期资源。参数 `chain` 是链头节点，不是“下一个中间件”；需要访问下一环节时用 `getNext()`。

链结构在 `build()` 时已经确定，不建议在 `init` 里改 `next` 指针。初始化完成后所有节点被锁定，之后再调用 `setNext(...)` 只会打印一条警告，不会生效。

应用关闭时会按链头到链尾的顺序调用每个中间件的 `destroy()`，默认空实现。中间件在 `init` 里创建了需要关闭的资源时才覆写它：

```java
public class MetricsMiddleware extends Middleware {

    private ScheduledExecutorService reporter;

    @Override
    public void init(Middleware chain) {
        reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleWithFixedDelay(this::flush, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public Object invoke(HttpContext ctx) {
        return next(ctx);
    }

    @Override
    public void destroy() {
        if (reporter != null) {
            reporter.shutdownNow();
            reporter = null;
        }
    }

    private void flush() {
        // 上报逻辑
    }
}
```

单个中间件的 `destroy` 抛出异常只记录日志，不影响其他中间件的销毁。`destroy` 需要可重复调用，重复关闭自己持有的资源不应报错。

## 6. 示例：访问日志与耗时

```java
public class AccessLogMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        long start = System.nanoTime();
        String method = ctx.message().method().name();
        String path = ctx.message().url().url(true);
        try {
            return next(ctx);
        } finally {
            long costMicros = (System.nanoTime() - start) / 1000;
            System.out.printf("%s %s %d us%n", method, path, costMicros);
        }
    }
}
```

`url(true)` 只取路径，不带查询串与片段。`finally` 保证控制器抛异常时也能记录到本次请求的耗时。

```bash
curl http://localhost:8080/user
# 控制台输出：
# GET /user 342 us
```

耗时包含路由匹配、参数绑定与控制器执行，不含响应写出的时间。

## 7. 分支中间件

`BranchMiddleware` 把一条链拆成若干条分支，运行时按键选择其中一条执行，执行完自动接回主链。分支键由子类根据请求计算：

```java
public class RoleBranchMiddleware extends BranchMiddleware {

    @Override
    protected String getBranchKey(HttpContext ctx) {
        String role = ctx.message().header("X-Role");
        return role == null ? "user" : role;
    }
}
```

分支在注册前添加，然后作为一个普通中间件注册到主链：

```java
RoleBranchMiddleware branch = new RoleBranchMiddleware();
branch.addMiddleware("admin", new AdminAuditMiddleware())
      .addMiddleware("admin", new AdminSqlLogMiddleware())
      .addMiddleware("user", new UserAuditMiddleware());

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .middleware(branch)
        .build();
```

请求带 `X-Role: admin` 时依次执行两个 admin 分支中间件，带其他值或没有该头时执行 user 分支，分支结束后继续执行主链中该中间件之后的节点。

几点规则：

- 同一分支内的中间件按添加顺序执行；同一个实例重复添加到同一分支，`addMiddleware` 当场抛 `IllegalStateException`。
- `getBranchKey` 返回的分支没有注册过时，抛 `RouteHandlerException`（错误码 `RouterNotMatch`），默认得到 404，也可以由异常处理器接管。
- 分支链在应用构建期完成组装、初始化与锁定，运行期不能增删分支成员。
- `BranchMiddleware` 自身也只有一个实例，`getBranchKey` 同样要按请求计算，不能把分支键存到字段里。

[首页](../README.md) | [异常处理器](./exceptionhandler.md) | [Cookie](./cookie.md)
