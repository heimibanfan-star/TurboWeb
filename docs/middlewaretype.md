# 中间件类型订阅

TurboWeb 提供了一类 **类型感知型中间件（Typed Middleware）**，用于根据上游返回值的类型，对请求结果进行**选择性订阅或忽略处理**。
这一机制大幅提升了中间件的可扩展性与代码整洁性，使得开发者无需在业务逻辑中显式编写类型判断或分支逻辑。

## 概述

在常规中间件开发中，开发者往往需要通过 `instanceof` 等方式判断返回值类型，从而决定是否执行特定逻辑。
这种实现方式不仅使中间件逻辑变得冗长，而且容易造成关注点混乱。

TurboWeb 的 **类型订阅机制** 提供了解决方案：
开发者可以通过泛型声明，仅订阅某一特定类型的返回值，框架会自动在运行时进行匹配，从而确保中间件仅在感兴趣的类型上被触发。

TurboWeb 内置三类类型感知中间件：

| 中间件类型               | 说明                                                    |
| ------------------------ | ------------------------------------------------------- |
| `TypedMiddleware<T>`     | 仅在返回值为指定类型 `T` 时触发                         |
| `TypedSkipMiddleware<T>` | 在返回值为指定类型 `T` 时跳过执行                       |
| `MixedMiddleware`        | 同时支持同步与异步（Reactive Stream）结果的类型感知处理 |

## 快速开始

### 订阅特定类型的返回值

当你只关心某类返回结果（如 `String`），可以使用 `TypedMiddleware<T>`：

```java
public static void main(String[] args) {
    LambdaRouterManager routerManager = new LambdaRouterManager();
    routerManager.addGroup(new LambdaRouterGroup() {
        @Override
        protected void registerRoute(RouterRegister register) {
            register.get("/01", ctx -> "Hello World");
            register.get("/02", ctx -> 10L);
        }
    });

    BootStrapTurboWebServer.create()
            .http()
            .routerManager(routerManager)
            // 只关注于String类型
            .middleware(new TypedMiddleware<String>() {
                @Override
                protected String afterNext(HttpContext ctx, String result) {
                    System.out.println(result);
                    return result;
                }
            })
            .and()
            .start(8080);
}
```

这个例子中我们可以看到，只有访问/01的时候才会打印内容，因为我们只订阅了String类型，也就是泛型类型。

### 跳过指定类型的返回值

有时我们希望**忽略某一类型**的处理逻辑，可以使用 `TypedSkipMiddleware<T>`：

```java
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        // 忽略对Long类型的处理
        .middleware(new TypedSkipMiddleware<Long>() {
            @Override
            protected Object afterNext(HttpContext ctx, Object result) {
                System.out.println(result);
                return result;
            }
        })
        .and()
        .start(8080);
```

### 同步与异步类型的混合处理

在响应式编程（Reactive Stream）场景下，返回结果可能是 `Mono<T>` 或 `Flux<T>`。
为了同时支持同步与异步类型，TurboWeb 提供了 `MixedMiddleware`：

```java
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        // 只关注于String类型
        .middleware(new MixedMiddleware() {
            @Override
            protected Object afterSyncNext(HttpContext ctx, Object result) {
                System.out.println("同步的逻辑处理：" + result);
                return result;
            }

            @Override
            protected Publisher<?> afterAsyncNext(HttpContext ctx, Publisher<?> publisher) {
                if (publisher instanceof Mono<?> mono) {
                    return mono.doOnNext(result -> System.out.println("异步的逻辑处理：" + result));
                }
                return publisher;
            }
        })
        .and()
        .start(8080);
```

## 扩展方法

这三个中间件统一继承了 `CoreTypeMiddleware` 因此有一些扩展的方法：

```java
/**
 * 前置钩子方法
 * 可在调用下一个中间件之前执行逻辑，例如权限检查、条件拦截等。
 *
 * @param ctx 当前请求上下文
 * @return true 继续执行下一个中间件，false 中断链条
 */
protected boolean preNext(HttpContext ctx) {
    return true;
}

/**
 * 链条中断时的回调方法
 * 可在 preNext 返回 false 时执行自定义逻辑。
 *
 * @param ctx 当前请求上下文
 * @return 中断后的返回值，默认 null
 */
protected Object onBreak(HttpContext ctx) {
    return null;
}
```

通过这两个方法，开发者可在中间件层实现更精细的控制逻辑，例如：

- 动态权限检查
- 请求频控与防刷
- 自定义拦截响应（例如直接返回 403 Forbidden）



[首页](../README.md) | [多版本路由控制](./mvrc.md)