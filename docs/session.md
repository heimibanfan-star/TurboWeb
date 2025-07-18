# Session

Session 是服务端用于记录用户状态的机制。当用户首次访问服务器时，服务端会创建一个唯一的会话标识（Session ID），并通过 Cookie 返回给客户端。之后客户端的每次请求都会携带这个 Session ID，服务器便可识别用户身份并保存其相关状态信息，如登录状态、购物车内容等。

与 Cookie 相比，Session 存储在服务端，安全性更高，适合保存敏感数据。

在 TurboWeb 中，Session 管理由 `HttpSession` 负责。

## 快速上手

**_设置session：_**

```java
@Get
public String setSession(HttpContext context) {
    HttpSession httpSession = context.httpSession();
    httpSession.setAttr("name", "turboweb");
    return "setSession";
}
```

session的设置是通过 `HttpSession` 的 `setAttr(..)` 来设置的。

**_获取session：_**

```java
@Get("/get")
public String getSession(HttpContext context) {
    HttpSession httpSession = context.httpSession();
    String name = (String) httpSession.getAttr("name");
    return "getSession: " + name;
}
```

如果可以确定类型可以在获取的时候指定类型：

```java
String name = httpSession.getAttr("name", String.class);
```

**_删除session:_**

```java
@Get("/rem")
public String removeSession(HttpContext context) {
    HttpSession httpSession = context.httpSession();
    httpSession.remAttr("name");
    return "removeSession";
}
```

**_当然，设置session的时候也是支持过期时间的：_**

```java
@Get("/setttl")
public String setSessionTTL(HttpContext context) {
    HttpSession httpSession = context.httpSession();
    httpSession.setAttr("name", "turboweb", 10000);
    return "setSessionTTL";
}
```

例如这里设置10秒过期。

## Session垃圾回收器

TurboWeb 默认使用内存实现的 Session 管理器，为防止内存泄漏，内置了垃圾回收器。你可以通过以下方式配置其参数：

```java
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        .and()
        .configServer(config -> {
            // 设置session检查的最小阈值
            config.setSessionCheckThreshold(1000);
            // 设置session检查的间隔时间
            config.setSessionCheckTime(1000 * 60 * 5);
            // 当session在规定时间内没有使用作为过期处理
            config.setSessionMaxNotUseTime(1000 * 60 * 60 * 24);
        })
        .start(8080);
```

> ⚠ **注意事项：**
>
> 垃圾回收过程中为确保数据一致性，TurboWeb 会获取写锁，阻塞所有请求线程，直到清理结束。因此请避免将清理间隔设置过小，以免影响系统吞吐性能。

## 黑洞Session管理器(BackHoleSessionManager)

如果你不使用 Session，可以启用 `BackHoleSessionManager` 来完全关闭 Session 功能，以获得更高的性能：

```java
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        .replaceSessionManager(new BackHoleSessionManager())
        .and()
        .start(8080);
```

✅ 使用该管理器后：

- 所有 `HttpSession` 操作将被忽略；
- 不再获取读锁；
- 垃圾回收器不会启动；
- 性能最优。

适合纯 RESTful 或无状态服务场景。

## 自定义Session管理器

如果你希望将 Session 存储到 Redis、MySQL 等外部系统，可自定义 Session 管理器，只需实现 `SessionManager` 接口：

```java
public class MySessionManager implements SessionManager {
    @Override
    public void setAttr(String sessionId, String key, Object value) { }

    @Override
    public void setAttr(String sessionId, String key, Object value, long timeout) { }

    @Override
    public Object getAttr(String sessionId, String key) { return null; }

    @Override
    public <T> T getAttr(String sessionId, String key, Class<T> clazz) { return null; }

    @Override
    public void remAttr(String sessionId, String key) { }

    @Override
    public boolean exist(String sessionId) { return false; }

    @Override
    public boolean createSessionMap(String sessionId) { return false; }

    @Override
    public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) { }

    @Override
    public String sessionManagerName() { return "MySessionManager"; }

    @Override
    public void expireAt(String sessionId) { }
}
```

注册自定义管理器：

```java
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        .replaceSessionManager(new MySessionManager())
        .and()
        .start(8080);
```

**_实现 `sessionGC()` 示例_**

```java
@Override
public void sessionGC(long checkTime, long maxNotUseTime, long sessionNumThreshold) {
    ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    service.scheduleWithFixedDelay(() -> {
        Locks.SESSION_LOCK.writeLock().lock();
        try {
            // 回收 session 的逻辑
        } finally {
            Locks.SESSION_LOCK.writeLock().unlock();
        }
    }, checkTime, checkTime, TimeUnit.MILLISECONDS);
}
```

确保在 GC 期间加写锁，避免并发修改。

> 需要注意的是，在分布式部署场景下，本地锁机制仅能限制当前节点的并发访问，无法保障对共享 Session 存储的全局互斥。例如，多个服务器实例可能同时访问或修改同一 Session 数据，即使本地加锁也无法避免数据冲突或不一致。在这种场景下，若使用 Redis 等具备过期策略的存储引擎，建议直接基于 Key 的 TTL（过期时间）自动清除过期 Session，而不是通过后台线程进行显式垃圾回收。此方式具有更高的效率和一致性，且无需引入复杂的分布式锁机制。



[首页](../README.md) | [Cookie](./cookie.md) | [ Server-Sent Events(SSE)](./sse.md)

