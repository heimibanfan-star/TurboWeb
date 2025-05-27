# <img src="../image/logo.png"/>

# 生命周期相关

## 监听器

监听器可以监听服务器启动的阶段。

创建两个监听器：

```java
package org.example.lifecycle;

import top.turboweb.core.listener.TurboWebListener;

public class OneListener implements TurboWebListener {
	@Override
	public void beforeServerInit() {
		System.out.println("服务器初始化之前-one");
	}
	@Override
	public void afterServerStart() {
		System.out.println("服务器启动之后-one");
	}
}
```

```java
package org.example.lifecycle;

import top.turboweb.core.listener.TurboWebListener;

public class TwoListener implements TurboWebListener {
	@Override
	public void beforeServerInit() {
		System.out.println("服务器初始化之前-two");
	}
	@Override
	public void afterServerStart() {
		System.out.println("服务器启动之后-two");
	}
}
```

注册监听器

```java
public static void main(String[] args) {
    TurboWebServer server = new StandardTurboWebServer(LifeCycleApplication.class);
    server.listeners(new OneListener(), new TwoListener());
    server.start();
}
```

监听器会按照注册的顺序来执行，控制台如下：

```text
服务器初始化之前-one
服务器初始化之前-two
17:09:12 [INFO ] [main] t.t.c.s.StandardTurboWebServer - TurboWeb初始化前置监听器方法执行完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultHttpClientInitializer - HttpClient初始化完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultExceptionHandlerInitializer - 异常处理器初始化成功
17:09:12 [INFO ] [main] t.t.h.s.DefaultSessionManagerProxy - session管理器初始化成功:MemorySessionManager
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultSessionManagerProxyInitializer - session管理器初始化完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultMiddlewareInitializer - http分发器初始化成功
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultMiddlewareInitializer - 中间件依赖注入完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultMiddlewareInitializer - 中间件初始化方法执行完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultMiddlewareInitializer - 中间件锁定完成
17:09:12 [INFO ] [main] t.t.c.i.i.DefaultHttpSchedulerInitializer - http调度器初始化成功
服务器启动之后-one
服务器启动之后-two
17:09:12 [INFO ] [nioEventLoopGroup-3-1] t.t.c.s.StandardTurboWebServer - TurboWeb启动后监听器方法执行完成
17:09:12 [INFO ] [nioEventLoopGroup-3-1] t.t.c.s.StandardTurboWebServer - TurboWebServer start on: http://0.0.0.0:8080, time: 314ms
```

TurboWeb默认会执行一个监听器是对Jackson进行初始化，如果用户不想让TurboWeb执行这个监听器可以关闭TurboWeb执行默认的监听器：

```java
server.executeDefaultListener(false);
```

该操作表示告诉TurboWeb不要执行内置的监听器，只执行用户手动注册的监听器。

## 中间件的初始化

TurboWeb 中间件的初始化分为 **四个阶段**，确保在服务启动前完成依赖注入、初始化逻辑和结构封锁，防止运行时中间件链结构发生变更。

**第一阶段：结构的构建**

TurboWeb 会根据用户注册的中间件，**按注册顺序**构建成链式结构，并设置每个中间件的 `next` 引用，形成完整的调用链。

**第二阶段 依赖注入**

TurboWeb 提供了多种 `Aware` 接口供中间件实现，用于在框架初始化时自动注入框架内部组件。

例如，以下中间件实现了 `SessionManagerProxyAware` 接口，用于注入会话管理器代理：

```java
package org.example.lifecycle;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;
import top.turboweb.http.middleware.aware.SessionManagerProxyAware;
import top.turboweb.http.session.SessionManagerProxy;

public class MyMiddleware extends Middleware implements SessionManagerProxyAware {
	@Override
	public Object invoke(HttpContext ctx) {
		return next(ctx);
	}

	@Override
	public void setSessionManagerProxy(SessionManagerProxy sessionManagerProxy) {
		System.out.println(sessionManagerProxy);
		System.out.println("注入Session管理器代理对象");
	}
}
```

注册中间件之后重新启动服务器可以看到如下内容：

```text
top.turboweb.http.session.DefaultSessionManagerProxy@2dc9b0f5
注入Session管理器代理对象
```

表示中间件在该阶段进行了依赖注入。

**第三阶段 中间件初始化方法调用**

TurboWeb 会按顺序调用每个中间件的 `init(Middleware chain)` 方法：

```java
@Override
public void init(Middleware chain) {
    System.out.println("初始化中间件");
}
```

参数 `chain` 是当前构建完成的中间件链的起点。

这是用户在中间件结构被封锁前**唯一一次修改中间件链的机会**，可用于动态插入、调整链结构（不推荐常规使用）。

**第四阶段 中间件的封锁阶段**

在完成上述三个阶段后，TurboWeb 会**封锁所有中间件结构**：

- 后续任何位置调用 `setNext(..)` 方法将不再生效。
- 中间件链结构正式定型，不允许在运行时修改，以保证线程安全和稳定性。



[目录](./guide.md) [服务器参数配置](./serverconfig.md) 上一节 下一节 [节点共享](./nodeshare.md)