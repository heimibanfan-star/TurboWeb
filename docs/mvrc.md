# 多版本路由控制

`VersionRouterManager` 是 TurboWeb 提供的 **多版本路由分发器**，用于在同一套服务中同时管理多个版本的路由逻辑，例如 `v1`、`v2` 等。
该功能常用于 **灰度发布（Canary Release）**、**A/B 测试** 或 **API 版本迁移** 等场景。

## 快速开始

假设我们有两个不同版本的 `UserController`，分别代表 V1 和 V2 的接口实现。

```java
package org.example.mvrc.v1;

import top.turboweb.anno.method.Get;
import top.turboweb.anno.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v1版本";
    }
}

package org.example.mvrc.v2;

import top.turboweb.anno.method.Get;
import top.turboweb.anno.Route;

@Route("/user")
public class UserController {

    @Get
    public String index() {
        return "v2版本";
    }
}
```

构建多版本路由管理器：

使用 `AnnoRouterManager` 分别注册不同版本的控制器，然后通过 `VersionRouterManager` 管理它们：

```java
public static void main(String[] args) {

    // 创建两个路由管理器
    AnnoRouterManager v1Manager = new AnnoRouterManager(true);
    v1Manager.addController(new org.example.mvrc.v1.UserController());
    AnnoRouterManager v2Manager = new AnnoRouterManager(true);
    v2Manager.addController(new org.example.mvrc.v2.UserController());
    // 创建版本控制路由管理器
    VersionRouterManager routerManager = new VersionRouterManager() {
        @Override
        protected RouterManager getRouterManager(HttpContext context, Managers managers) {
            // 这里我们以随机的方式让新版本低频率的访问
            int num = new Random().nextInt();
            if (num % 3 == 0) {
                return managers.getRouterManager("v2");
            }
            return managers.getRouterManager("v1");
        }
    };
    // 将路由管理器放入多版本控制路由管理器中
    routerManager.addRouterManager("v1", v1Manager);
    routerManager.addRouterManager("v2", v2Manager);

    BootStrapTurboWebServer.create()
            .http()
            .routerManager(routerManager)
            .and()
            .start(8080);
}
```

这样可以让一少部分的流量首先去v2版本尝试，适合于灰度发布。

## 工作原理

`VersionRouterManager` 是 `RouterManager` 的一个特殊实现，内部通过一个 `Managers` 对象维护多个版本的路由管理器：

- 每个版本的 `RouterManager`（如 `AnnoRouterManager`）独立维护自己的路由表；
- 请求进入后，由 `getRouterManager(HttpContext, Managers)` 动态选择一个版本；
- 被选中的版本路由器再执行对应的 Controller 方法。

你可以通过任意逻辑决定版本选择，例如：

- 根据请求参数（`version=xxx`）；
- 根据 Header（如 `X-Api-Version`）；
- 根据 A/B 测试算法；
- 根据灰度规则（部分用户优先使用新版本）。

### 示例：基于请求参数选择版本

```java
VersionRouterManager routerManager = new VersionRouterManager() {
    @Override
    protected RouterManager getRouterManager(HttpContext context, Managers managers) {
        String version = context.query("version");
        if (version == null) {
            version = "v1";
        }
        return managers.getRouterManager(version);
    }
};
```

访问：

```text
GET /user?version=v1  →  返回 “v1版本”
GET /user?version=v2  →  返回 “v2版本”
```

### 实例：灰度发布

通过随机或百分比控制，将少部分流量导向新版本：

```java
@Override
protected RouterManager getRouterManager(HttpContext context, Managers managers) {
    int ratio = new Random().nextInt(100);
    if (ratio < 10) { // 10% 流量走 v2
        return managers.getRouterManager("v2");
    }
    return managers.getRouterManager("v1");
}
```

这样可以实现“**渐进式上线**”：

> 在正式替换之前，仅让部分用户先体验新版本，观察稳定性与性能。

## 最佳实践建议

- 将 **每个版本的 Controller 放在独立的包中**；
- 使用 **`AnnoRouterManager` 自动绑定** 控制器；
- 避免在 `VersionRouterManager` 内写死版本逻辑，建议抽象出策略类；
- 使用配置或数据库驱动的版本路由，可支持在线灰度切换。



[首页](../README.md) | [HTTP客户端](./httpclient.md) | [中间件类型订阅](./middlewaretype.md)