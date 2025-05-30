# <img src="../image/logo.png"/>

# Session

Session 是 Web 应用中用于在多个请求之间保持用户状态的机制。HTTP 协议本身是无状态的，每一次请求之间都是独立的，服务器无法区分来自同一用户的连续请求。为了解决这一问题，服务器通常会为客户端创建一个会话标识符（Session ID），并将其保存在客户端的 Cookie 中。每次请求时，客户端通过 Cookie 自动携带该标识，服务器即可识别并关联对应的会话数据。

Session 主要用于存储用户登录状态、操作过程中的中间数据或其他需要跨请求共享的信息。相比于 Cookie 直接在客户端存储数据，Session 数据保存在服务器内存或持久化存储中，具有更好的安全性和数据隔离性。

接下来看一下在TurboWeb中如何使用Session。

**创建一个Controller接口，保存Session:**

```java
@Get("/set")
public String set(HttpContext c) {
    HttpSession httpSession = c.getHttpSession();
    httpSession.setAttr("name", "turboweb");
    return "set session";
}
```

在 TurboWeb 中，通过 `HttpContext` 的 `getHttpSession()` 方法可以获取到 `HttpSession` 对象。所有 Session 的操作都依赖于这个对象来完成。

- 通过 `HttpSession` 的 `setAttr(..)` 方法可以设置 Session 数据，第一个参数是键（key），第二个参数是值（value）。

通过访问 `http://localhost:8080/user/set` 可以设置 Session。

**获取存储的session**

```java
@Get("/get")
public String get(HttpContext c) {
    HttpSession session = c.getHttpSession();
    String name = (String) session.getAttr("name");
    return "session name: " + name;
}
```

获取 Session 数据可以通过 `HttpSession` 的 `getAttr(..)` 方法，传入对应的键（key）即可。

由于返回值类型是 `Object`，因此你需要根据实际情况进行类型转换。

如果你希望获取的 Session 数据类型更明确，可以使用另一个重载版本：

```java
@Get("/get2")
public String get2(HttpContext c) {
    HttpSession session = c.getHttpSession();
    String name = session.getAttr("name", String.class);
    return "session name: " + name;
}
```

第二个参数是数据的类型，可以确保返回值的类型安全。

**支持设置过期时间的 Session**

在某些应用场景中，如验证码等临时数据，Session 需要设置过期时间。TurboWeb 支持设置带过期时间的 Session。

```java
@Get("/setttl")
public String setttl(HttpContext c) {
    HttpSession session = c.getHttpSession();
    session.setAttr("name", "turboweb", 10000);
    return "set session ttl";
}
```

过期时间的单位是毫秒。上面的例子中，Session 会在 10 秒后自动过期。你可以通过访问 `http://localhost:8080/user/get` 来测试，10 秒后获取的 Session 内容将为 `null`。

**主动删除session数据**

TurboWeb 还支持开发者主动删除某个 Session 数据：

```java
@Get("/remove")
public String remove(HttpContext c) {
    SseResponse sseResponse = c.newSseResponse();
    HttpSession session = c.getHttpSession();
    session.remAttr("name");
    return "remove session";
}
```

通过 `HttpSession` 的 `removeAttr(..)` 方法，可以删除指定键（key）对应的 Session 数据。

## Session的存储原理

TurboWeb 的 `HttpSession` 对象不再直接存储 Session 数据，而是通过 `SessionManager` 来管理数据的存储。`HttpSession` 仅仅作为 `SessionManager` 的代理对象，内部只存储 `JSESSIONID`。所有对 `HttpSession` 的操作都会被委派给 `SessionManager` 执行，因此用户可以根据具体需求灵活替换 Session 的存储方案。

**MemorySessionManager**

`MemorySessionManager` 是 TurboWeb 默认的 Session 管理器，它使用内存来存储 Session 数据。该管理器会自动进行垃圾回收。在垃圾回收期间，为了确保 Session 容器的安全，它会通过抢占写锁的方式避免回收期间发生并发请求。虽然这种方式可以保护数据，但可能会导致短时间的停顿。

**BackHoleSessionManager**

对于一些无状态的系统，可能根本不需要 Session。在这种情况下，`BackHoleSessionManager` 是一个不错的选择。如果将管理器替换为 `BackHoleSessionManager`，那么所有对 Session 的操作都会被忽略，获取 Session 的内容时也会返回 `null`。

替换方式如下：

```java
server.replaceSessionManager(new BackHoleSessionManager());
```

**用户自定义**

如果 TurboWeb 提供的 Session 管理器无法满足需求，用户可以通过实现 `SessionManager` 接口来自定义自己的 Session 管理器。然后，通过替换原有的 Session 管理器来使用自定义的方案。



[目录](./guide.md) [Cookie](./cookie.md) 上一节 下一节 [SSE的支持](./sse.md)