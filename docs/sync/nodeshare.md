# <img src="../image/logo.png"/>

# 节点共享

TurboWeb 提供了去中心化的“节点共享”能力，允许多个服务节点之间共享功能与路由，从而在无中心注册表和无统一网关的场景下实现服务之间的调用与协作。

**什么是节点共享？**

“节点共享”是指：

> 每个 TurboWeb 服务节点可以暴露自己的能力给其他节点，并可访问其他节点暴露的能力，形成去中心化的服务协作网络。

通过配置协作节点，TurboWeb 可以根据设定的匹配规则，将接收到的请求动态地转发到其他远程节点，或交由当前节点本地的 HTTP 调度器处理。

这种机制特别适用于以下场景：

- 某一模块被进一步拆分为多个服务，但子服务数量较少；
- 不希望为其引入独立的注册中心或 API 网关；
- 希望多个节点组合对外表现为单一功能服务的多实例部署。

例如，当两个节点分别提供不同但互补的功能模块时，配置节点共享后，对外部调用方而言，这两个节点看起来就像是同一个服务的两个副本，不需要关心其内部功能的具体分布。

## 使用节点共享

假设我们有两个服务，分别是用户服务和订单服务。

用户服务：

```java
@RequestPath("/user")
public class UserController {
	@Get
	public String user(HttpContext c) {
		return "user service";
	}
}
```

```java
public class UserApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(UserApplication.class);
		server.controllers(new UserController());
		server.start(8080);
	}
}
```

订单服务：

```java
@RequestPath("/order")
public class OrderController {
	@Get
	public String order(HttpContext c) {
		return "order service";
	}
}
```

```
public class OrderApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(OrderApplication.class);
		server.controllers(new OrderController());
		server.start(8081);
	}
}
```

接下来我们先配置用户服务的节点共享：

```java
public class UserApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(UserApplication.class);
		server.controllers(new UserController());
		
		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/order", "http://localhost:8081");
		server.gateway(gateway);
		
		server.start(8080);
	}
}
```

配置节点共享的对象是 `GateWay` 具体的策略通过该对象的 `addServerNode(..)` 进行添加，参数1是拦截的前缀，参数2是拦截该前缀的请求转发的目的节点。

这里的意思是所有以 `/order` 开头的请求都转发到 `http://localhost:8081` 去处理。

接下来配置订单服务的节点共享：

```java
public class OrderApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(OrderApplication.class);
		server.controllers(new OrderController());

		Gateway gateway = new DefaultGateway();
		gateway.addServerNode("/user", "http://localhost:8080");
		server.gateway(gateway);

		server.start(8081);
	}
}
```

启动服务，发送如下4个请求：

```http
GET http://localhost:8080/user
```

```http
GET http://localhost:8080/order
```

```http
GET http://localhost:8081/user
```

```http
GET http://localhost:8081/order
```

可以发现这四个请求都已经正确的处理了，在外界来看好像这俩服务器好像就是同一个实例的多个副本。



[目录](./guide.md) [生命周期相关](./lifecycle.md) 上一节