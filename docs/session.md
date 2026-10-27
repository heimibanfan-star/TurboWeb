# Session

Session 把用户状态存在服务端，客户端只保存一个会话标识。首次写入会话属性时，服务端生成标识并通过 `JSESSIONID` Cookie 下发；之后的请求带着它回来，服务端据此找到同一份会话数据。

与 Cookie 相比，Session 的数据不经过客户端，适合放登录状态、购物车这类不应被篡改的内容。

TurboWeb 的会话入口是 `HttpContext#httpSession()`，底层由可替换的 `SessionManager` 存储，默认实现是进程内存。

## 变更说明

- 会话管理器改由 `HttpRequestApplication.Builder#sessionManager(...)` 替换，旧的 `BootStrapTurboWebServer...replaceSessionManager(...)` 已不存在。
- 回收参数从服务器全局配置移到 `HttpConfig`，通过应用的 `.config(HttpConfig.builder()...)` 设置。
- `HttpSession` 的属性读写接口保持不变；新增 `sessionSafeScope`，供自定义管理器与垃圾回收互斥。

## 1. 读写会话

```java
@Route("/session")
public class SessionController {

    @Get("/login")
    public String login(HttpContext ctx) {
        HttpSession session = ctx.httpSession();
        session.setAttr("user", "alice");
        return "login";
    }

    @Get("/me")
    public String me(HttpContext ctx) {
        String user = ctx.httpSession().getAttr("user", String.class);
        return user == null ? "not login" : user;
    }

    @Get("/logout")
    public String logout(HttpContext ctx) {
        ctx.httpSession().remAttr("user");
        return "logout";
    }
}
```

用 curl 走一遍，`-c` 保存响应下发的 Cookie，`-b` 在后续请求中带回：

```bash
curl -i -c cookie.txt http://localhost:8080/session/login
# HTTP/1.1 200 OK
# Set-Cookie: JSESSIONID=2f1c...; Path=/; HttpOnly
#
# login

curl -b cookie.txt http://localhost:8080/session/me
# alice

curl -b cookie.txt http://localhost:8080/session/logout

curl -b cookie.txt http://localhost:8080/session/me
# not login
```

`HttpSession` 也可以直接声明为控制器形参，框架按类型自动注入：

```java
@Get("/me")
public String me(HttpSession session) {
    String user = session.getAttr("user", String.class);
    return user == null ? "not login" : user;
}
```

注入的是当前请求的会话对象，与 `ctx.httpSession()` 返回的是同一个。注入按声明类型精确匹配，形参要写 `HttpSession` 本身，写成 `DefaultHttpSession` 不会被注入。

`httpSession()` 永远返回对象，不需要判空。会话标识只在第一次写属性时才生成：只调用 `getAttr` 不会创建会话，也不会下发 `JSESSIONID`。

`getAttr` 在会话不存在、属性不存在或已过期时返回 `null`。指定类型的版本在类型不符时抛 `ClassCastException`：

```java
String user = session.getAttr("user", String.class);
```

`remAttr` 删除单个属性，属性本来就不存在时无效果。`remAttr` 不会为尚未创建的会话生成标识。

## 2. 带过期时间的属性

`setAttr(key, value, timeout)` 的 `timeout` 单位是毫秒，从设置时刻开始计算：

```java
@Get("/captcha")
public String captcha(HttpContext ctx) {
    ctx.httpSession().setAttr("code", "839201", 5 * 60 * 1000);
    return "sent";
}
```

过期后的属性读取返回 `null`，实际清理发生在会话回收线程的执行周期里，因此内存占用不会在过期瞬间消失。属性的过期与会话本身的过期是两回事，前者只影响单个键。

## 3. JSESSIONID 的写出

每次请求开始时，框架从请求 Cookie 中取 `JSESSIONID`，交给 `SessionManager` 校验。会话存在就复用，不存在或已过期就当作新会话，等下一次写属性时重新生成。

请求结束时，满足下面任一条件就写出一条 `Set-Cookie`：

- 本次请求新建了会话，标识与请求带来的不一致（请求没带 `JSESSIONID` 也算）；
- 本次请求调用了 `session.setPath(...)`，路径发生变化。

写出的形式固定为：

```text
JSESSIONID=<sessionId>; Path=<sessionPath>; HttpOnly
```

不带 `Max-Age`，属于浏览器会话 Cookie，关闭浏览器后失效。会话路径默认 `/`，可以在控制器里改：

```java
@Get("/app/login")
public String login(HttpContext ctx) {
    HttpSession session = ctx.httpSession();
    session.setPath("/app");
    session.setAttr("user", "alice");
    return "login";
}
```

改过路径后，浏览器只在 `/app` 及其子路径下携带该 Cookie。路径只决定浏览器的发送范围，不改变服务端按标识查找会话的逻辑。

每个经过中间件链的请求结束时，框架都会对已有会话调用一次续期（更新最后使用时间），因此持续访问的会话不会被判为不活跃。

## 4. 配置回收参数

三个参数都在 `HttpConfig` 上，通过应用的 `.config(...)` 传入：

```java
HttpConfig config = HttpConfig.builder()
        .sessionCheckTime(60_000)        // 检查间隔，单位毫秒
        .sessionMaxNotUseTime(1_800_000) // 最大不活跃时长，单位毫秒
        .sessionCheckThreshold(256)      // 触发检查的会话数量阈值
        .build();

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .config(config)
        .build();
```

| 参数 | 默认值 | 约束 | 作用 |
| --- | --- | --- | --- |
| `sessionCheckTime` | `300000` | 不小于 `1000` | 回收线程多久检查一次 |
| `sessionMaxNotUseTime` | `-1` | 无 | 会话空闲超过该时长即视为过期，单位毫秒 |
| `sessionCheckThreshold` | `256` | 不小于 `1` | 会话总数低于该值时跳过本次检查 |

回收的实际行为：

- 每隔 `sessionCheckTime` 毫秒执行一次，会话总数小于 `sessionCheckThreshold` 时直接跳过。
- 达到阈值后取写锁，逐个检查：空闲时间超过 `sessionMaxNotUseTime` 的会话整体删除，其余会话清理其中已过期的属性。
- 取写锁期间，正在处理请求的会话访问会被阻塞，检查结束后恢复。检查间隔不要设得太小。

`sessionMaxNotUseTime` 的默认值 `-1` 需要特别注意：判定条件是“当前时间减去最后使用时间大于该值”，负数对任何会话都成立。也就是说，一旦会话数达到阈值，第一次回收就会把所有会话清掉。需要会话长期存活时，务必显式配置一个正数。

会话是单机内存存储，进程重启即丢失，多个实例之间也不共享。

## 5. 关闭会话

完全不用会话时，换成黑洞管理器，省掉存储与回收开销：

```java
import top.turboweb.http.core.session.BackHoleSessionManager;

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .sessionManager(new BackHoleSessionManager())
        .build();
```

它的所有属性操作都是空操作：写入不保存，读取返回 `null`，会话存在性判断返回 `false`，回收线程也不会启动。注意写入属性仍然会生成一个会话标识并随响应下发 `JSESSIONID`，只是这个标识在服务端查不到任何数据。

## 6. 自定义 SessionManager

把会话放到 Redis、数据库等外部存储时，实现 `SessionManager` 接口后用 `.sessionManager(...)` 替换：

```java
import top.turboweb.http.session.SessionManager;

import java.util.function.Function;

public class MySessionManager implements SessionManager {

    @Override
    public void setAttr(String sessionId, String key, Object value) {
        // 写入永久属性
    }

    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) {
        // 写入带过期时间的属性，timeout 单位毫秒
    }

    @Override
    public Object getAttr(String sessionId, String key) {
        // 会话或属性不存在、已过期时返回 null
        return null;
    }

    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) {
        // 类型不符时抛 ClassCastException
        return null;
    }

    @Override
    public void remAttr(String sessionId, String key) {
        // 删除属性，属性不存在时无效果
    }

    @Override
    public boolean exist(String sessionId) {
        // 会话是否存在且未过期
        return false;
    }

    @Override
    public boolean createSessionMap(String sessionId) {
        // 会话不存在时创建并返回 true，已存在返回 false
        return false;
    }

    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
        // 应用构建期调用一次，回收线程由此启动
    }

    @Override
    public String sessionManagerName() {
        return "MySessionManager";
    }

    @Override
    public void expireAt(String sessionId) {
        // 更新会话的最后使用时间
    }

    @Override
    public <T> T sessionSafeScope(Function<SessionManager, T> action) {
        return action.apply(this);
    }
}
```

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .sessionManager(new MySessionManager())
        .build();
```

实现时注意几点：

- `sessionGC` 在应用构建期调用一次，默认持有者会把三个配置参数原样传进来，回收线程由实现方自己启动。
- 请求处理全程包在 `sessionSafeScope` 里，进入时取读侧、退出时释放，异常也要释放。实现内部有共享状态和后台回收线程时，回收线程要取写侧，与请求互斥；没有共享状态、也没有回收线程的实现直接执行回调即可，如上面的写法。
- 本地锁只能约束单个节点。多实例共享存储时，更可靠的做法是依赖存储引擎的键过期（例如 Redis 的 TTL），而不是在每个节点上跑一套显式回收。
- 管理器会被所有请求并发调用，实现必须线程安全。

[首页](../README.md) | [Cookie](./cookie.md) | [中间件的使用](./middleware.md)
