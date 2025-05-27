# <img src="../image/logo.png"/>

# Cookie

Cookie 是浏览器保存的一小段文本数据，由服务器通过响应头设置，客户端在后续请求中自动携带，用于标识用户或保存状态。它通常用于实现用户登录状态保持、偏好设置记忆等功能。

服务器通过设置 Cookie 响应头将数据写入浏览器，浏览器则在同源请求中自动将 Cookie 附加在请求头中发回。每个 Cookie 包含键值对，还可以指定作用路径、有效域名、过期时间、是否仅限 HTTPS（Secure）、是否禁止 JavaScript 访问（HttpOnly）等属性。

Cookie 体积小、数量有限，更适合存储轻量级数据。通常，它与服务器端会话（Session）配合使用，实现安全、持久的用户状态管理。

接下来看一下TurboWeb中的Cookie如何使用。

Cookie的设置：

```java
@Get("/example01")
public String example01(HttpContext c) {
    HttpCookie httpCookie = c.getHttpCookie();
    httpCookie.setCookie("name", "turbo");
    return "example01";
}
```

`HttpContext` 的 `getHttpCookie()` 方法用来获取Cookie的操作对象。

通过 `HttpCookie` 的 `setCookie(..)` 方法可以设置Cookie，参数1为key，参数2为value。

接下来通过浏览器访问：`http://localhost:8080/user/example01` 可以查看Cookie被成功设置。

Cookie的获取：

由于上一个例子中已经进行了Cookie的设置，接下来我们来获取Cookie。

```java
@Get("/example02")
public String example02(HttpContext c) {
    HttpCookie httpCookie = c.getHttpCookie();
    String name = httpCookie.getCookie("name");
    return "example02:" + name;
}
```

Cookie的获取是通过 `HttpCookie` 的 `getCookie(..)` 方法来获取，参数是key的名称。

接下来访问浏览器的 `http://localhost:8080/user/example02` 就可以看到Cookie的内容了。



[目录](./guide.md) [中间件的使用](./middleware.md) 上一节 下一节 [Session](./session.md)