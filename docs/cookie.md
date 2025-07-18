# Cookie

`Cookie` 是 Web 开发中用于在客户端存储小量状态数据的机制，常用于会话保持（如登录信息）。

服务器通过响应头的 `Set-Cookie` 向浏览器设置 Cookie；浏览器随后在每次请求中通过请求头 `Cookie` 自动带回，完成状态同步。

在 TurboWeb 中，Cookie 的管理由框架内置的 `HttpCookieManager` 负责，开发者通过 `HttpContext.cookie()` 获取该管理器即可完成大多数操作。

## 快速上手

**_设置 Cookie_**

```java
@Get("/set")
public String setCookie(HttpContext context) {
    context.cookie().setCookie("name", "turbo");
    return "setCookie";
}
```

通过 `HttpContext.cookie()` 获取 `HttpCookieManager`，使用 `setCookie(key, value)` 设置 Cookie。

**_读取 Cookie_**

```java
@Get("/get")
public String getCookie(HttpContext context) {
    String name = context.cookie().getCookie("name");
    return "name: " + name;
}
```

使用 `getCookie(key)` 读取 Cookie 值，返回的是字符串类型，若不存在返回 `null`。

**_删除 Cookie_**

```java
@Get("/rem")
public String removeCookie(HttpContext context) {
    context.cookie().removeCookie("name");
    return "remCookie";
}
```

调用 `removeCookie(key)` 删除 Cookie，其本质是在响应中添加一个 `Max-Age=0` 的 Cookie，让浏览器立即清除。

## 进阶用法

### 设置 Cookie 属性

你可以使用 `HttpCookie` 对象或函数式写法灵活设置属性，如过期时间、路径等。

**_方式一：手动构建Cookie_**

```java
@Get("/set2")
public String setCookie2(HttpContext context) {
    HttpCookieManager cookieManager = context.cookie();
    cookieManager.setCookie("name", "turbo", cookie -> {
        cookie.setMaxAge(3600);
        cookie.setPath("/user");
    });
    return "setCookie2";
}
```

**_方式二：函数式配置属性（推荐）_**

```java
context.cookie().setCookie("name", "turbo", cookie -> {
    cookie.setMaxAge(3600);
    cookie.setPath("/user");
});
```

### 清除待写入的 Cookie

```java
@Get("/clear")
public String clearToWriteCookies(HttpContext context) {
    HttpCookieManager cookieManager = context.cookie();
    cookieManager.setCookie("a", "1");
    cookieManager.setCookie("b", "2");
    cookieManager.setCookie("c", "3");
    cookieManager.clearToWriteCookies();
    return "clearCookie";
}
```

该方法会清除**当前请求中尚未写回响应的 Cookie**，即使调用了 `setCookie()`，最终也不会写入响应。

### 删除所有 Cookie（包括客户端传入的）

```java
@Get("/clearAll")
public String clearAllCookies(HttpContext context) {
    context.cookie().clearAll();
    return "clearAllCookie";
}
```

调用 `clearAll()` 会清空：

- 所有已设置待写入的 Cookie
- 所有从客户端传来的 Cookie（会在响应中以 `Max-Age=0` 删除）

> **⚠ 无法删除非 `/` 路径下的 Cookie**
>
> 由于浏览器的 Cookie 匹配机制，只有与当前请求路径一致或更宽泛的 Cookie 才会随请求发送。
> 因此，服务器只能清除 `path=/` 的 Cookie，对于设置了其他路径（如 `/user`、`/admin` 等）的 Cookie，无法统一删除。



[首页](../README.md) | [运行信息的获取](./serverinfo.md) | [Session](./session.md)