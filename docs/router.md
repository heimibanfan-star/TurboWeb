# 路由体系

TurboWeb 的路由由路由管理器负责管理，所有的路由管理器均继承自 `RouterManager` 类。

框架支持两种路由方式：**声明式路由** 和 **编程式路由**。

- **编程式路由** 基于 Lambda 表达式构建，具备极高的执行性能，但使用相对复杂，适合对性能要求极高的场景。
- **声明式路由** 使用注解进行标注，语义清晰，使用便捷。其底层依赖反射与方法句柄，因此在初始化与调用阶段的性能略逊于编程式路由。

开发者可根据具体需求，在易用性与性能之间权衡，选择合适的路由管理器。

## 声明式路由

### 基本使用

**1.创建Controller**

```java
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
    @Get
    public String getUser(HttpContext context) {
        return "Get User";
    }
}
```

**2.编写启动类**

```java
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.AnnoRouterManager;

public class Application {
    public static void main(String[] args) {
        AnnoRouterManager routerManager = new AnnoRouterManager();
        routerManager.addController(new UserController());
        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and().start();
    }
}
```

**3.发送请求**

```http
GET http://localhost:8080/user
```

`AnnoRouterManager` 是基于声明式注解的路由管理器。开发者通过调用路由管理器的 `addController()` 方法，将编写好的 Controller 注册到路由系统中。

声明式路由通过注解定义路由信息，管理器对 Controller 有以下要求：

- Controller 类必须标注 `@RequestPath` 注解，且该注解必须存在。
- Controller 中每个路由方法必须且仅接受一个参数，且类型为 `HttpContext`。
- 路由方法必须是 `public` 修饰，且标注了以下 HTTP 方法注解中的至少一个：`@Get`、`@Post`、`@Patch`、`@Put`、`@Delete`。

### 注解介绍

`@RequestPath` 类注解: 用于标注控制器类的路由前缀。

HTTP 方法注解（`@Get`, `@Post`, `@Put`, `@Patch`, `@Delete`）: 用于标注控制器中对应 HTTP 请求方式的方法路由路径。

## 编程式路由

### 基本使用

**1.编写controller，继承路由组**

```java
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.LambdaRouterGroup;

public class OrderController extends LambdaRouterGroup {
    @Override
    public String requestPath() {
        return "/order";
    }

    @Override
    protected void registerRoute(RouterRegister register) {
        register.get(this::getOrder);
    }

    public String getOrder(HttpContext context) {
        return "Get Order";
    }
}
```

**2.编写启动类**

```java
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.http.middleware.router.LambdaRouterManager;

public class Application {
    public static void main(String[] args) {
        LambdaRouterManager routerManager = new LambdaRouterManager();
        routerManager.addGroup(new OrderController());
        BootStrapTurboWebServer.create()
                .http().routerManager(routerManager)
                .and().start();
    }
}
```

**3.发送请求**

```http
GET http://localhost:8080/order
```

`LambdaRouterManager` 是 TurboWeb 提供的基于编程式路由的管理器，实现灵活且高性能的路由注册与分发。

编程式路由使用要求：

- 自定义 Controller 必须继承 `LambdaRouterGroup`
- 重写 `registerRoute(RouterRegister register)` 方法，并且在该方法中注册路由。
- 路由处理函数必须接受唯一参数 `HttpContext`，用于访问请求和响应上下文。

`requestPath()`：可以自己选择是否重写，如果不重写，默认作为""来处理。

注册时可支持定义子路径

```java
@Override
protected void registerRoute(RouterRegister register) {
    register.get("/show", this::getOrder);
}
```

### 方法介绍

`requestPath()`：用于表示当前路由组的请求前缀。

`registerRoute(RouterRegister register)`：用于注册路由:

- `register.get(...)`
   注册 GET 请求处理函数。
- `register.post(...)`
   注册 POST 请求处理函数。

- `register.put(...)`
   注册 PUT 请求处理函数。
- `register.patch(...)`
   注册 PATCH 请求处理函数。
- `register.delete(...)`
   注册 DELETE 请求处理函数。

## 路由路径规则

TurboWeb的路由路径有两部分组成，分别是类路径和方法路径，例如声明式中 `@RequestPath` 定义了类路径，`@Get` 等注解定义方法路径；编程式中 `requestPath(...)` 定义类路径，`get(...)` 等方法定义了方法路径。

在介绍如下的规则时，类路径用**prePath**来表示，方法路径用**subPath**来表示。

**`请求路径 = prePath + subPath`**

| 类路径 (prePath) | 方法路径 (subPath) | 说明                                 | 最终请求路径      |
| ---------------- | ------------------ | ------------------------------------ | ----------------- |
| `null`           | `null`             | 类路径和方法路径都为 null            | `/`               |
| `null`           | `""`               | 类路径为 null，方法路径为空字符串    | `/`               |
| `null`           | `/`                | 类路径为 null，方法路径为根路径 `/`  | `/`               |
| `""`             | `null`             | 类路径为空字符串，方法路径为 null    | `/`               |
| `""`             | `""`               | 类路径为空字符串，方法路径为空字符串 | `/`               |
| `""`             | `/`                | 类路径为空字符串，方法路径为 `/`     | `/`               |
| `/`              | `null`             | 类路径为 `/`，方法路径为 null        | `/`               |
| `/`              | `""`               | 类路径为 `/`，方法路径为空字符串     | `/`               |
| `/`              | `/`                | 类路径和方法路径都为 `/`             | `/`               |
| `/user`          | `null`             | 类路径 `/user`，方法路径为 null      | `/user`           |
| `/user`          | `""`               | 类路径 `/user`，方法路径为空字符串   | `/user`           |
| `/user`          | `/`                | 类路径 `/user`，方法路径为 `/`       | `/user`           |
| `/user/`         | `/list`            | 类路径 `/user/`，方法路径 `/list`    | `/user/list`      |
| `user`           | `list`             | 类路径无 `/`，方法路径无 `/`         | `/user/list`      |
| `/user`          | `list/`            | 类路径 `/user`，方法路径末尾有 `/`   | `/user/list`      |
| `/user/`         | `/list/`           | 两者末尾都带 `/`                     | `/user/list`      |
| `/user`          | `/list/{id}`       | 带路径变量                           | `/user/list/{id}` |
| `/user`          | `/list/*`          | 包含非法字符 `*`，应抛异常           | 抛出异常          |
| `/user`          | `/../list`         | 包含非法字符 `..`，应抛异常          | 抛出异常          |

类路径 `prePath` 如果为 `null`、`""` 或 `/`，都视为根路径 `""`（空字符串）。

方法路径 `subPath` 如果为 `null`、`""` 或 `/`，都视为根路径 `/`。

拼接时，自动添加必要的 `/`，并去除多余的 `/`，保证路径格式规范。

任何包含 `*` 或 `..` 的路径都被视为非法，会抛出异常。



[首页](../README.MD) | [快速入门——第一个“Hello World”](./quickstart.md)  | [请求数据的处理](./request.md)

