# 路由体系

TurboWeb 只提供注解声明式路由。控制器类上的 `@Route` 声明类前缀，公有方法上的 `@Get` 等注解声明请求方式与方法路径，两者拼成最终路径。

## 变更说明

重构前框架有注解路由和 lambda 编程式路由两套实现，二者零共享代码：路径拼接、匹配、注册和参数取用各写一套；注解侧同时存在语义重叠的 `@RequestPath` 与 `@Route`；指定解析器还有独立的 `@Parser` 机制。同一份路径规则要在两处维护，校验口径和扩展点也各有一套，行为容易分叉。

重构把请求处理收敛为一条链路：只保留注解路由，由 `RestRouteRegister` 统一登记控制器与参数解析器，由 `RestRouteTree` 统一匹配，由参数绑定器统一取数。路径规则、构建期冲突校验、异常错误码都只有一份实现，扩展点也只对着一套接口。lambda 路由整面下线，`@RequestPath` 废止，没有生产调用点的 `@Parser` 机制一并移除。

本文只写重构后的写法。

## 1. 定义控制器

```java
package demo;

import top.turboweb.rest.annotation.binder.PathParam;
import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Post;
import top.turboweb.rest.annotation.router.Route;

@Route("/user")
public class UserController {

    @Get("/list")
    public String list() {
        return "user list";
    }

    @Get("/:id")
    public String detail(@PathParam("id") Long id) {
        return "user " + id;
    }

    @Post
    public String create() {
        return "created";
    }
}
```

这个控制器登记了三条路由：

| 请求方式 | 路径 | 对应方法 |
| --- | --- | --- |
| GET | `/user/list` | `list` |
| GET | `/user/:id` | `detail` |
| POST | `/user` | `create` |

`@Post` 没有写路径，用的是注解默认值 `/`，与类前缀 `/user` 合并后就是 `/user`。

扫描范围是类的公有方法（`Class#getMethods()`），包括从父类、接口继承来的公有方法。私有方法不会被扫描，也不会报错，只是不产生路由。

## 2. 登记路由

```java
RestRouteRegister routeRegister = new RestRouteRegister();
routeRegister.registerRoute(new UserController());

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .build();
```

`registerRoute` 有四个重载：

```java
registerRoute(Object instance);                          // 按 instance.getClass() 扫描
registerRoute(Class<?> targetClass, Object instance);    // 按指定类扫描，调用落在 instance 上
registerRoute(List<Object> instances);                   // 逐个按运行时类登记
registerRoute(Map<Class<?>, Object> routes);             // 显式指定扫描类与实例
```

按指定类扫描的用法适用于面向接口编程：注解写在接口方法上，实现类通过 `registerRoute(UserApi.class, userServiceImpl)` 登记。

同一个类只能登记一次。重复登记在 `registerRoute` 调用时直接抛 `IllegalStateException`，不会等到启动阶段。

## 3. 注解

| 注解 | 标注位置 | 含义 | 默认值 |
| --- | --- | --- | --- |
| `@Route` | 类 | 类路径前缀 | `""` |
| `@Get` | 方法 | GET 请求及其方法路径 | `"/"` |
| `@Post` | 方法 | POST 请求及其方法路径 | `"/"` |
| `@Put` | 方法 | PUT 请求及其方法路径 | `"/"` |
| `@Patch` | 方法 | PATCH 请求及其方法路径 | `"/"` |
| `@Delete` | 方法 | DELETE 请求及其方法路径 | `"/"` |

一个方法只能标一个请求方式注解。标了两个以上，构建路由表时抛 `IllegalArgumentException`。

控制器类可以不标 `@Route`，此时类路径为空；但方法路径必须让最终结果落在某个具体路径上，不能是根路径 `/`。

## 4. 路径拼接

最终路径按 **统一前缀 + 类路径 + 方法路径** 的顺序拼接，每一级都可能是空值：

- `null`、空串、`/` 都表示这一级没有路径段，不占位置；
- 缺少前导 `/` 时自动补上；
- 结尾的单个 `/` 自动去掉；
- 任意一级出现连续 `//` 属于非法路径，构建路由表时抛 `IllegalArgumentException`；
- 三级都提不出路径段时结果是根路径 `/`，框架不支持根路由，构建时同样抛 `IllegalArgumentException`。

示例：

| `RestRouteRegister` 前缀 | `@Route` | 方法注解 | 最终路径 |
| --- | --- | --- | --- |
| 无 | `/user` | `@Get`（默认 `/`） | `/user` |
| 无 | `/user` | `@Get("/list")` | `/user/list` |
| 无 | `user` | `@Get("list")` | `/user/list` |
| 无 | `/user/` | `@Get("/list/")` | `/user/list` |
| 无 | `""` | `@Get("/user/list")` | `/user/list` |
| 无 | `/user` | `@Get("/:id")` | `/user/:id` |
| `/api` | `/user` | `@Get("/list")` | `/api/user/list` |
| 无 | `""` | `@Get`（默认 `/`） | 构建失败：不支持根路由 |
| 无 | `/user` | `@Get("/list//detail")` | 构建失败：连续斜杠 |

## 5. 路径参数

路径中以 `:` 开头的整段是参数段，例如 `/:id`、`/:userId/orders/:orderId`。

参数名必须匹配 `[A-Za-z_][A-Za-z0-9_]*`，也就是字母或下划线开头，后面只能是字母、数字、下划线。同一条路径里参数名不能重复；不含参数的字面量段里不允许出现 `:`。

```java
@Route("/user")
public class UserController {

    @Get("/:userId/orders/:orderId")
    public String order(@PathParam("userId") Long userId,
                        @PathParam("orderId") String orderId) {
        return userId + ": " + orderId;
    }
}
```

```bash
curl "http://localhost:8080/user/7/orders/A1001"
# 7: A1001
```

`@PathParam` 的形参类型只能是下列简单类型：

`String`、`Integer`/`int`、`Long`/`long`、`Double`/`double`、`Boolean`/`boolean`、`LocalDate`、`LocalDateTime`、`LocalTime`。

数值类型按 `Integer.parseInt`、`Long.parseLong` 这类方法转换；三个时间类型按 ISO 格式调用 `parse`。声明成其他类型时 `@PathParam` 不会被识别，参数会绑成 `null`。

匹配成功后，参数值按 `HttpConfig` 的缺省字符集做百分号解码。解码失败（例如转义不完整）返回 400。值本身无法转成声明类型（例如 `/:id` 传了 `abc` 却声明为 `Long`）会抛运行时异常，默认按 500 处理。

## 6. 匹配规则

一次请求进来后，路由只拿 **路径部分** 做匹配，查询串和片段不参与。

- 路径按 `/` 分段，段数必须与注册路径完全一致，`/user/list/extra` 不会匹配 `/user/list`；
- 请求路径末尾的 `/` 会被忽略，`/user/list/` 与 `/user/list` 等价；请求路径中重复的斜杠也会被忽略，`/user//list` 同样等价于 `/user/list`；
- 字面量段按原始路径逐字比较，区分大小写，也不做百分号解码；
- 请求方式必须与方法的注解一致。

### 字面量优先

同一个请求可能同时落在多条模式上，框架从路径开头逐段比较候选，在第一个出现差异的段上，字面量段优先于参数段。

```java
@Route("/user")
public class UserController {

    @Get("/list")
    public String list() {
        return "user list";
    }

    @Get("/:id")
    public String detail(@PathParam("id") String id) {
        return "user " + id;
    }
}
```

`GET /user/list` 命中 `list()`，`GET /user/42` 命中 `detail()`。

这条规则按第一个差异段生效，不是"字面量总比参数多"：`/a/:x/c` 与 `/a/b/:y` 同时注册时，`GET /a/b/c` 命中后者，因为第二个段上后者是字面量 `b`。

### 未命中

路径没有对应处理器，或者路径存在但没有该请求方式的处理器，都按路由未匹配处理：返回 404，响应体是框架生成的 HTML 错误页。

OPTIONS 请求由框架直接返回 200，用于 CORS 预检，不进入路由匹配，也不经过中间件。

## 7. 同一路径绑定多个请求方式

同一个 URL 上的不同请求方式可以分开写在多个方法上，构建时会合并进同一个路由处理器：

```java
@Route("/user")
public class UserController {

    @Get("/:id")
    public String get(@PathParam("id") Long id) {
        return "get " + id;
    }

    @Put("/:id")
    public String update(@PathParam("id") Long id) {
        return "update " + id;
    }

    @Delete("/:id")
    public String delete(@PathParam("id") Long id) {
        return "delete " + id;
    }
}
```

同一个 URL 上同一个请求方式只能出现一次。两条方法登记出重复的 `GET /user/:id` 时，构建阶段直接失败。

## 8. 构建期会拒绝的写法

以下问题都在 `HttpRequestApplication.Builder#build()` 生成路由表时暴露，不会等到请求进来才报错：

| 写法 | 结果 |
| --- | --- |
| 一个方法同时标 `@Get` 和 `@Post` | `IllegalArgumentException`：`declares multiple request method annotations` |
| 路径中出现连续 `//` | `IllegalArgumentException`：`consecutive slashes are not allowed` |
| 前缀、类路径、方法路径合并后是 `/` | `IllegalArgumentException`：`root path routing is not supported` |
| 同一个 URL、同一个请求方式登记了两次 | `IllegalArgumentException`：`Route repeat` |
| `/user/:id` 与 `/user/:name` 同时注册 | `IllegalArgumentException`：`Ambiguous path pattern` |
| 同一个类注册了两次 | `IllegalStateException`：`is already registered` |
| 调用 `build()` 之后继续 `registerRoute` | `IllegalStateException`：`Route register is already produced` |

`/user/:id` 与 `/user/:name` 被拒绝的原因是参数名不参与匹配：两条模式逐段形状相同，任何请求都无法区分该走哪个方法，因此按歧义处理。

## 9. 统一前缀与冻结

统一前缀在构造登记表时给出，前缀末尾的 `/` 会被自动裁掉：

```java
RestRouteRegister routeRegister = new RestRouteRegister("/api");
```

`null` 与空串表示不加前缀。前缀内部含连续斜杠（如 `/api//v1`）不会在构造时报错，而是在构建路由表时按非法路径拒绝。

路由表在 `newRouteHandlerManager(jsonSerializer)` 时生成，`HttpRequestApplication.Builder#build()` 内部会调用它。生成之后登记表即冻结：继续登记控制器、替换参数解析器、指定校验器都会抛 `IllegalStateException`。因此控制器和各项配置必须在 `build()` 之前全部加完。

[首页](../README.md) | [快速入门](./quickstart.md) | [请求体系](./request.md)
