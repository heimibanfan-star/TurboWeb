# 拦截器的使用

在 TurboWeb 中，拦截器管理器本质上是一个特殊的中间件。尽管已有中间件机制，但拦截器针对特定场景做了优化，弥补了中间件的部分不足。

**_为什么需要拦截器？_**

中间件虽灵活，但存在两个明显局限：

- **生命周期需手动控制**：请求进入 Controller 前、处理后、完成时等阶段，需手动编写逻辑区分，不够直观；

- **无 URL 路径区分**：所有请求都会经过中间件，无法针对特定路径执行逻辑。

拦截器则解决了这些问题：

- 内置请求生命周期回调（如前置处理、后置处理），无需手动控制阶段；
- 支持 URL 路径匹配，可精准拦截指定路径的请求（如`/user/**`）。

## 拦截器的基础用法

**_定义拦截器_**

拦截器需实现 `InterceptorHandler` 接口，重写三个生命周期方法和优先级方法：

```java
public class OneInterceptor implements InterceptorHandler {
    /**
     * 请求进入Controller前执行（前置处理）
     * @return true：继续执行后续拦截器和Controller；false：中断流程
     */
    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("one preHandler");
        return true;
    }

    /**
     * Controller执行完成后、响应返回前执行（后置处理）
     * @param result Controller的返回结果，可在此修改
     */
    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("one postHandler");
        return result; // 可返回修改后的结果
    }

    /**
     * 整个请求完成后执行（无论成功/失败），适合资源清理
     * @param exception 异常对象（无异常时为null）
     */
    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("one afterCompletion");
    }

    /**
     * 执行优先级：值越小，优先级越高（必须唯一）
     */
    @Override
    public int order() {
        return 0;
    }
}

// 定义第二个拦截器（逻辑类似，order设为1）
public class TwoInterceptor implements InterceptorHandler {
    @Override
    public boolean preHandler(HttpContext ctx) {
        System.out.println("two preHandler");
        return true;
    }

    @Override
    public Object postHandler(HttpContext ctx, Object result) {
        System.out.println("two postHandler");
        return result;
    }

    @Override
    public void afterCompletion(Throwable exception) {
        System.out.println("two afterCompletion");
    }

    @Override
    public int order() {
        return 1; // 优先级低于OneInterceptor
    }
}
```

**_定义 Controller 接口_**

```java
@RequestPath("/user")
public class UserController {
    @Get
    public String getUser(HttpContext ctx) {
        System.out.println("getUser（Controller执行）");
        return "user";
    }
}
```

**_注册拦截器与 Controller_**

通过 `InterceptorManager` 注册拦截器，并指定匹配的 URL 路径：

```java
// 1. 初始化路由管理器并注册Controller
AnnoRouterManager routerManager = new AnnoRouterManager();
routerManager.addController(new UserController());

// 2. 初始化拦截器管理器并注册拦截器
InterceptorManager interceptorManager = new InterceptorManager();
// 拦截所有以/user/开头的路径（**匹配任意多级路径）
interceptorManager.addInterceptionHandler("/user/**", new OneInterceptor());
interceptorManager.addInterceptionHandler("/user/**", new TwoInterceptor());

// 3. 启动服务器（拦截器管理器作为中间件注册）
BootStrapTurboWebServer.create()
        .http()
        .middleware(interceptorManager) // 注册拦截器中间件
        .routerManager(routerManager)   // 注册路由管理器
        .and()
        .start();
```

**_执行结果与顺序分析_**

访问 `http://localhost:8080/user` 后，控制台输出如下：

```text
one preHandler    // OneInterceptor前置处理（order=0，优先级高）
two preHandler    // TwoInterceptor前置处理（order=1，优先级低）
getUser（Controller执行）  // 所有前置处理完成后，执行Controller
two postHandler   // TwoInterceptor后置处理（逆序执行）
one postHandler   // OneInterceptor后置处理（逆序执行）
two afterCompletion // TwoInterceptor完成后处理（逆序执行）
one afterCompletion // OneInterceptor完成后处理（逆序执行）
```

**`preHandler`**：按 `order` 从小到大执行（优先级高的先执行）；

**`postHandler`** 和 **`afterCompletion`**：按 `order` 从大到小执行（逆序，优先级低的先执行）。

## 拦截器的路径匹配规则

拦截器通过 URL 路径匹配决定是否执行，支持两种通配符：

- `*`：匹配单级路径（如`/user/*` 匹配 `/user/123`，但不匹配 `/user/123/detail`）；
- `**`：匹配任意多级路径（如`/user/**` 匹配 `/user/123`、`/user/123/detail`等）。

## 拦截器的流程控制

`preHandler` 方法的返回值决定后续流程是否继续：

| `preHandler` 返回值 | 流程影响                                                     |
| ------------------- | ------------------------------------------------------------ |
| true                | 继续执行后续拦截器的 `preHandler` → 执行 Controller → 执行所有拦截器的 `postHandler` → 执行所有拦截器的 `afterCompletion` |
| `false` 或抛出异常  | 中断后续流程：<br /> - 后续拦截器的 `preHandler` 不执行；<br /> - 后续中间件和Controller不执行； <br />- 所有拦截器的 `postHandler` 不执行； <br />- `afterCompletion` 从**最后一个返回`true`的拦截器**开始，逆序执行（确保资源清理）。 |



[首页](../README.md) | [中间件的使用](./middleware.md) | [静态资源的支持](./staticresource.md)

