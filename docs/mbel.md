# 多分支执行管线

TurboWeb 提供了一种 **多分支执行管线（BranchMiddleware）** 机制，
允许中间件链在运行时根据上下文条件（如 Header、路径、租户标识、权限等级等）
动态选择不同的分支链路执行逻辑，并在分支执行完毕后自动合并回主链。

这种机制使得框架能够灵活地实现 **多租户路由、权限隔离、模块分流** 等复杂场景，
同时保持主中间件管线的统一结构。

## 功能概述

在传统的中间件执行模型中，请求从头到尾依次经过一条固定的管线：

```text
MiddlewareA → MiddlewareB → MiddlewareC → MiddlewareD
```

而 BranchMiddleware 允许根据条件“分支执行”，例如：

```text
MiddlewareA
   ↓
 [BranchMiddleware]
   ├── admin 分支 → M1 → M2
   └── user 分支 → N1 → N2
   ↓
MiddlewareD
```

当请求触发时，开发者可以根据上下文（如请求头、参数、Session 等）动态选择分支，
执行完分支链后，再合并回主链继续执行。

## 快速上手

以下示例展示了如何根据 `Authorization` 请求头动态选择不同的中间件分支。

```java
public static void main(String[] args) {
    // ========== 定义分支1（admin）中间件 ==========
    Middleware m1forb1 = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("branch1-middleware1");
            return next(ctx);
        }
    };
    Middleware m2forb1 = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("branch1-middleware2");
            return next(ctx);
        }
    };

    // ========== 定义分支2（user）中间件 ==========
    Middleware m1forb2 = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("branch2-middleware1");
            return next(ctx);
        }
    };
    Middleware m2forb2 = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("branch2-middleware2");
            return next(ctx);
        }
    };

    // ========== 定义分支中间件 ==========
    BranchMiddleware branchMiddleware = new BranchMiddleware() {
        @Override
        protected String getBranchKey(HttpContext ctx) {
            // 通过 Authorization 头部判断分支
            return ctx.getRequest().headers().get(HttpHeaderNames.AUTHORIZATION);
        }
    };

    // 注册分支链路
    branchMiddleware
            .addMiddleware("admin", m1forb1)
            .addMiddleware("admin", m2forb1)
            .addMiddleware("user", m1forb2)
            .addMiddleware("user", m2forb2);

    // ========== 主链中间件 ==========
    Middleware beforeBranch = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("分支之前执行...");
            return next(ctx);
        }
    };
    Middleware afterBranch = new Middleware() {
        @Override
        public Object invoke(HttpContext ctx) {
            System.out.println("分支之后执行...");
            return "Hello World";
        }
    };

    // 启动服务器
    BootStrapTurboWebServer.create()
            .http()
            .middleware(beforeBranch)
            .middleware(branchMiddleware)
            .middleware(afterBranch)
            .and()
            .start();
}
```

**请求 1：Authorization = admin**

```http
GET http://localhost:8080
Authorization: admin
```

结果如下：

```text
分支之前执行...
branch1-middleware1
branch1-middleware2
分支之后执行...
```

**请求 2：Authorization = user**

```http
GET http://localhost:8080
Authorization: user
```

结果如下：

```text
分支之前执行...
branch2-middleware1
branch2-middleware2
分支之后执行...
```

## 运行机制解析

BranchMiddleware 在内部维护两层结构：

| 字段               | 作用                             |
| ------------------ | -------------------------------- |
| `middlewareMap`    | 保存各分支及其中间件的注册顺序   |
| `middlewareChains` | 初始化阶段组装的实际中间件执行链 |

运行时步骤如下：

1. **根据上下文选择分支**
    调用 `getBranchKey(HttpContext ctx)` 获取当前请求对应的分支键。
2. **执行分支链路**
    调用 `middlewareChains.get(branchKey).invoke(ctx)` 执行该分支的第一个中间件。
3. **分支结束后合并回主链**
    每个分支尾部自动接上 `MergeMiddleware`，继续执行主链后续中间件。

## 生命周期与初始化

BranchMiddleware 在服务器启动阶段完成分支链组装。
每个分支的中间件依次通过 `init()` 初始化，并最终锁定链结构以防运行时修改。

在执行阶段：

- 若请求分支不存在，抛出 `TurboRouterException`；
- 若分支链中断（`setNext()` 未连接），未连接部分不会初始化；
- 所有分支共享同一个主链上下文，但互不干扰。

## 使用场景

- **多租户应用**：根据租户ID执行不同的认证与拦截逻辑；
- **权限分流**：根据用户角色进入不同的业务路径；
- **API分组**：不同模块独立中间件链，但共用同一服务器；
- **A/B测试**：动态选择不同分支链以测试策略差异。



[首页](../README.md) | [中间件类型订阅](./middlewaretype.md)