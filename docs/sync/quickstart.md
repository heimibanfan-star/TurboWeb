<img src="../image/logo.png"/> 

# 快速开始

## 第一个web程序

引入maven坐标

```xml
<dependency>
  <groupId>io.gitee.turboweb</groupId>
  <artifactId>turboweb-framework</artifactId>
  <version>Ox.2.2-RELEASE</version>
</dependency>
```

1.创建Controller接口

```java
package org.example.quickstart;

import top.turboweb.commons.Get;
import top.turboweb.commons.RequestPath;
import top.turboweb.http.HttpContext;

@RequestPath("/hello")
public class HelloController {
	@Get
	public String hello(HttpContext c) {
		return "Hello TurboWeb";
	}
}
```

2.创建服务启动类，并且添加创建的Controller接口

```java
package org.example.quickstart;

import top.turboweb.core.StandardTurboWebServer;
import top.turboweb.core.TurboWebServer;

public class QuickStartApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class);
		server.controllers(new HelloController());
		server.start();
	}
}
```

3.只后通过HTTP请求该接口

```http
GET http://localhost:8080/hello
```

可以看到返回了Hello TurboWeb。

### 这些代码做了什么事？

先来说一下这个Controller接口吧。

```java
@RequestPath("/hello")
public class HelloController {
	@Get
	public String hello(HttpContext c) {
		return "Hello TurboWeb";
	}
}
```

``@RequestPath("/hello")`` 

- 这是一个类级别的注解，用于标注当前类为一个控制器类（Controller）。
- 注解的参数 `"/hello"` 表示该控制器处理的路径前缀。
- 控制器中的所有方法请求路径，都会在这个前缀的基础上进行组合。

``@Get``

- 这是一个方法级别的注解，表示该方法用于处理 **HTTP GET 请求**。
- 框架在处理请求时，会根据 HTTP 方法类型（如 GET、POST）和路径来匹配对应的方法。

方法签名``public String hello(HttpContext c)``

- 该方法是控制器中定义的处理逻辑，用来响应客户端请求。
- 参数 `HttpContext c` 是请求上下文对象，提供了对请求参数、响应对象、会话等的访问接口。
- 返回值是一个字符串 `"Hello TurboWeb"`，表示响应体内容。根据框架规则，这将被直接写入响应中，作为纯文本返回给客户端。

完整的路由路径

- 路由路径是类注解 `@RequestPath("/hello")` 和方法注解 `@Get` 组合形成的。
- 如果 `@Get` 没有指定子路径，那么完整路径就是 `/hello`，即对 GET `/hello` 请求作出响应。

接下来看这一段启动类的代码

```java
public class QuickStartApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class);
		server.controllers(new HelloController());
		server.start();
	}
}
```

``TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class);``

- 创建一个 TurboWeb Web 服务器实例。
- `StandardTurboWebServer` 是 TurboWeb 提供的标准服务器实现类。
- 构造函数传入 `QuickStartApplication.class`，用于获取启动类的信息（如 classpath、资源路径等），通常在配置扫描或错误日志定位时有用。

``StandardTurboWebServer``还有另一个重载的构造器：

```java
TurboWebServer server = new StandardTurboWebServer(QuickStartApplication.class, 1);
```

- 这里的第二个参数是服务器实例的IO线程的数量，可以不配置，默认是单线程。

``server.controllers(new HelloController());``

- 注册一个控制器实例，这里注册的是前面定义的 `HelloController`。
- TurboWeb 通过这种方式将用户自定义的控制器挂载到路由系统中，使其能够响应 HTTP 请求。
- 支持注册多个控制器。

``server.start();``

- 启动 Web 服务器，绑定端口并开始监听来自客户端的 HTTP 请求。
- 启动成功后，服务器进入事件循环，等待并处理来自客户端的连接和请求。

这个``start()``方法也有不同的重载方法：

重载1：

```java
server.start(8080);
```

- 参数是服务器实例监听的端口，默认端口是8080。

重载2：

```java
server.start("0.0.0.0", 8080);
```

- 参数1：服务器监听的网卡地址，默认是0.0.0.0。
- 参数2：服务器监听的端口。



[目录](./guide.md) 下一节 [请求数据的处理](./request.md)

