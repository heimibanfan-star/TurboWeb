# Cookie

Cookie 是存放在客户端的一小段状态数据。服务端通过响应头的 `Set-Cookie` 下发，浏览器在后续请求中通过 `Cookie` 请求头自动带回。

TurboWeb 把当前请求的 Cookie 收在 `HttpCookies` 里，通过 `ctx.cookies()` 取得。同一个对象既能读取客户端带来的 Cookie，也能登记要写回响应的 Cookie，写回由框架在请求收尾时统一完成，业务代码不需要接触响应头。

## 变更说明

- Cookie 的读取与写入统一由 `ctx.cookies()` 提供，旧的响应对象写 Cookie 方式已不可用。
- Cookie 契约改为 `HttpCookie`，默认实现 `DefaultHttpCookie` 位于 `top.turboweb.http.cookie`，`Set-Cookie` 头由 Cookie 自身序列化，序列化不再依赖 Netty 响应对象。
- 写回时机固定在中间件链返回、结果转化完成之后，由编排器统一写出，控制器里调用 `setCookie` 即可。

## 1. 读取 Cookie

```java
@Route("/user")
public class UserController {

    @Get("/me")
    public String me(HttpContext ctx) {
        String uid = ctx.cookies().getCookie("uid");
        if (uid == null) {
            return "anonymous";
        }
        return "uid: " + uid;
    }
}
```

```bash
curl -i -H "Cookie: uid=42" http://localhost:8080/user/me
# HTTP/1.1 200 OK
#
# uid: 42

curl -i http://localhost:8080/user/me
# anonymous
```

`HttpCookies` 也可以直接声明为控制器形参，框架按类型自动注入，不需要注解：

```java
@Get("/me")
public String me(HttpCookies cookies) {
    String uid = cookies.getCookie("uid");
    return uid == null ? "anonymous" : "uid: " + uid;
}
```

注入的就是 `ctx.cookies()` 返回的同一个对象，两种写法完全等价，形参写法省去先取上下文。注入按声明类型精确匹配，形参要写 `HttpCookies` 本身，写成 `DefaultHttpCookies` 不会被注入。

`getCookie(key)` 返回字符串，名称不存在时返回 `null`。名称按原样精确匹配，大小写不同视为不同的 Cookie。

`getCookies()` 返回当前请求全部 Cookie 的名称与值，包括客户端带来的和本次请求用 `setCookie` 登记的：

```java
Map<String, String> all = ctx.cookies().getCookies();
```

控制器方法也可以用 `@CookieParam` 直接绑定 Cookie 值，只支持简单类型。名称不存在时包装类型注入 `null`，基本类型得到零值（`0`、`0.0`、`false`）：

```java
@Get("/from-cookie")
public String fromCookie(@CookieParam("uid") Long uid) {
    return uid == null ? "anonymous" : "uid: " + uid;
}
```

参数绑定的完整规则见[请求体系](./request.md)。

## 2. 设置 Cookie

```java
@Get("/set")
public String setCookie(HttpContext ctx) {
    ctx.cookies().setCookie("name", "turbo");
    return "setCookie";
}
```

```bash
curl -i http://localhost:8080/user/set
# HTTP/1.1 200 OK
# Set-Cookie: name=turbo; Path=/
#
# setCookie
```

`setCookie(key, value)` 使用默认属性：路径 `/`，不写 `Domain`，会话 Cookie（浏览器关闭即失效），不带 `HttpOnly`。同名 Cookie 再次设置会覆盖前一次。

需要属性时用带回调的重载，直接改 `HttpCookie`：

```java
@Get("/remember")
public String remember(HttpContext ctx) {
    ctx.cookies().setCookie("name", "turbo", cookie -> {
        cookie.setPath("/user");
        cookie.setMaxAge(7 * 24 * 3600);
        cookie.setHttpOnly(true);
    });
    return "remember";
}
```

也可以自己构造 `DefaultHttpCookie` 后交给 `setCookie(HttpCookie)`：

```java
HttpCookie cookie = new DefaultHttpCookie("name", "turbo");
cookie.setMaxAge(3600);
cookie.setHttpOnly(true);
ctx.cookies().setCookie(cookie);
```

可用属性：

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `path` | `/` | 浏览器只在匹配的路径下发送。传 `null` 或空串保留原值 |
| `domain` | 不写 | 默认不下发 `Domain`，即仅当前域名。传 `null` 或空串保留原值 |
| `maxAge` | `-1` | 单位秒。负数表示浏览器会话 Cookie；`0` 表示立即过期；正数表示存活秒数 |
| `httpOnly` | `false` | 为 `true` 时客户端脚本读不到该 Cookie |

当前 `HttpCookie` 没有 `Secure` 与 `SameSite` 属性，需要时应自行拼接 `Set-Cookie` 头。

Cookie 值在写出时按缺省字符集做 URL 编码，读取时不还原：同一次请求里 `getCookie` 拿到的仍是原值，但客户端在后续请求中带回的是编码后的字符串。需要稳定往返的值建议只用 ASCII 字母、数字与常见符号。

## 3. 删除 Cookie

```java
@Get("/logout")
public String logout(HttpContext ctx) {
    ctx.cookies().removeCookie("name");
    return "logout";
}
```

`removeCookie(key)` 分两种情况：

- Cookie 是本次请求用 `setCookie` 登记的：直接从待写集合中撤掉，响应里不出现该 Cookie。
- Cookie 来自客户端：写出 `name=; Path=/; Max-Age=0`，让浏览器删除。

名称不存在时什么也不做。

删除头固定使用 `Path=/`，因为框架不知道原 Cookie 的路径。如果客户端 Cookie 是以 `Path=/user` 下发的，这条删除头不会命中它。浏览器的 Cookie 本来就是按路径匹配发送的，只有请求路径在 Cookie 的路径范围内时，服务端才可能看到并清除它。

## 4. 清空

`clearToWriteCookies()` 撤销本次请求所有待写的 Cookie，但不会影响 `getCookie` 的读取结果：

```java
HttpCookies cookies = ctx.cookies();
cookies.setCookie("a", "1");
cookies.setCookie("b", "2");
cookies.clearToWriteCookies();   // 响应中不再有 a、b，但本请求内仍能读到
```

`clearAll()` 会同时处理两个方向：

```java
ctx.cookies().clearAll();
```

- 本次请求新登记的 Cookie 直接从集合中移除，不写出；
- 客户端带来的 Cookie 从集合中移除，并逐个写出 `Max-Age=0` 的删除头（同样固定 `Path=/`）。

## 5. 写回时机与边界

Cookie 在中间件链返回、返回值转化为响应之后写出，因此控制器和中间件里设置的 Cookie 都会生效，写回时已能看到本次请求的最终状态。具体边界：

- 中间件短路返回的响应同样会写出 Cookie，收尾逻辑与正常响应对应。
- 链内抛出的异常由异常处理器产出响应，该路径不经过 Cookie 写回，异常发生前设置的 Cookie 会丢失。
- CORS 头在 Cookie 之后由编排器写入，二者互不影响。
- 会话 Cookie（`JSESSIONID`）由框架单独写出，与 `ctx.cookies()` 的待写集合互不干扰，详见 [Session](./session.md)。

[首页](../README.md) | [Session](./session.md) | [请求体系](./request.md)
