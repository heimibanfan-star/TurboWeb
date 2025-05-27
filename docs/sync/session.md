# <img src="../image/logo.png"/>

# Session

Session 是 Web 应用中用于在多个请求之间保持用户状态的机制。HTTP 协议本身是无状态的，每一次请求之间都是独立的，服务器无法区分来自同一用户的连续请求。为了解决这一问题，服务器通常会为客户端创建一个会话标识符（Session ID），并将其保存在客户端的 Cookie 中。每次请求时，客户端通过 Cookie 自动携带该标识，服务器即可识别并关联对应的会话数据。

Session 主要用于存储用户登录状态、操作过程中的中间数据或其他需要跨请求共享的信息。相比于 Cookie 直接在客户端存储数据，Session 数据保存在服务器内存或持久化存储中，具有更好的安全性和数据隔离性。

接下来看一下在TurboWeb中如何使用Session。

创建一个Controller接口，保存Session:

```java
@Get("/set")
public String set(HttpContext c) {
    Session session = c.getSession();
    session.setAttribute("name", "turboweb");
    return "set session";
}
```

通过 `HttpContext` 的 `getSession()` 方法来获取 `Session` 对象，对Session的操作依赖该对象完成。

通过 `Session` 的 `setAttribute(..)` 方法来设置session的内容，参数1是key，参数2是value。

接下来通过浏览器访问 `http://localhost:8080/user/set` 就可以设置session了。

接下来看一下如果获取存储的session:

```java
@Get("/get")
public String get(HttpContext c) {
    Session session = c.getSession();
    String name = (String) session.getAttribute("name");
    return "session name: " + name;
}
```

session的获取是通过 `Session` 的 `getAttribute(..)` 方法来完成，参数是key。

session获取之后的类型是 `Object` 类型，因此需要用户根据实际情况进行类型转换。

通过上述的 `http://localhost:8080/user/set` 地址先设置session，然后通过访问 `http://localhost:8080/user/get` 就可以获取到存储的session内容了。

在TurboWeb中也支持存储带**过期时间**的session.。

为什么要有过期时间？

有的场景，例如存储一个验证码，需要用户在5分钟之内输入，超过5分钟，那么这个验证码就失效了，因此过期时间非常的重要。

`Session` 的 `setAttribute(..)` 方法提供了一个重载版本，多出的一个参数就是配置过期时间。

```java
@Get("/setttl")
public String setttl(HttpContext c) {
    Session session = c.getSession();
    session.setAttribute("name", "turboweb", 10000);
    return "set session ttl";
}
```

单位是**毫秒**，这里设置的就是10秒之后过期。

接下来可以通过 `http://localhost:8080/user/get` 来访问，等待10秒之后可以看到获取到的内容就是 `null` 了。

TurboWeb的session也支持开发者**主动删除**某个键值对。

```java
@Get("/remove")
public String remove(HttpContext c) {
    Session session = c.getSession();
    session.removeAttribute("name");
    return "remove session";
}
```

通过 `Session` 的 `removeAttribute(..)` 方法来删除指定的键值对，参数是key。



[目录](./guide.md) [Cookie](./cookie.md) 上一节 下一节 [SSE的支持](./sse.md)