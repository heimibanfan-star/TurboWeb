# <img src="../image/logo.png"/>

# HTTP客户端

TurboWeb 提供了两种内置的 HTTP 客户端，分别是：`PromiseHttpClient` 和 `ReactiveHttpClient`。

`PromiseHttpClient` 这是一个**面向同步编程风格**的 HTTP 客户端，适合使用虚拟线程（Loom）或传统线程的场景。调用方式简单直观，底层依然是基于 `reactor-netty` 实现的响应式网络通信，但对外屏蔽了响应式细节，用户只需关注请求和响应的业务逻辑即可。

`ReactiveHttpClient` 这是一个**完全响应式风格**的 HTTP 客户端，适合响应式框架和高并发、非阻塞场景。其请求和响应均使用 `Mono` 或 `Flux` 进行封装，与 `reactor` 生态无缝集成。

## 客户端的初始化

```java
public static void main(String[] args) throws ExecutionException, InterruptedException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    RestResponseResult<String> result = promiseHttpClient.get("http://127.0.0.1:8080/hello", null, String.class).get();
    String resultBody = result.getBody();
    System.out.println(resultBody);
}
```

如上列代码，在使用Http客户端的时候需要对其进行**初始化**，初始化代码为：

```java
HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
```

参数1：http客户端的配置对象。

参数2：执行http客户端的 `EventLoopGroup`

**如果你在TurboWebServer实例中使用HttpClient那么不需要进行初始化。**如下列的代码：

```java
public class ClientApplication {
	public static void main(String[] args) throws ExecutionException, InterruptedException {
		TurboWebServer server = new StandardTurboWebServer(ClientApplication.class);
		server.start(8081);

		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		RestResponseResult<String> result = promiseHttpClient.get("http://127.0.0.1:8080/hello", null, String.class).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}
}
```

这时因为TurboWeb在启动的过程中会自动对 `HttpClientUtils` 进行初始化，因此在TurboWeb服务器实例启动成功之后可以安全的使用HttpClient。

## 常用方法

这里就只 `PromiseHttpClient` 客户端的方法，因为 `PromiseHttpClient` 和 `ReactiveHttpClient` 唯一不同的就是返回值，方法的使用是一样的。

```java
public <T> Promise<RestResponseResult<T>> get(String url, HttpHeaders headers, Map<String, String> params, Class<T> type)
```

```java
public <T> Promise<RestResponseResult<T>> get(String url, Map<String, String> params, Class<T> type)
```

```java
public <T> Promise<RestResponseResult<Map>> get(String url, Map<String, String> params)
```

以GET方式发送请求。

`url` 请求的地址

`headers` 请求头的设置，没有设置null

`params` 查询参数，没有设置null

`type` 响应内容的格式，默认是Map

```java
public <T> Promise<RestResponseResult<T>> postJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type)
```

```java
public <T> Promise<RestResponseResult<T>> postJson(String url, Map<String, String> params, Object bodyContent, Class<T> type)
```

```java
public Promise<RestResponseResult<Map>> postJson(String url, Map<String, String> params, Object bodyContent)
```

发送POST请求，并且请求体的格式是 `application/json`

`url` 请求的地址

`headers` 请求头的设置，可以为null

`params` 查询参数，可以为null

`bodyContent` 请求体，传入一个实体对象或null

`type` 返回值的类型，默认是Map

```java
public <T> Promise<RestResponseResult<T>> postForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type)
```

```java
public <T> Promise<RestResponseResult<T>> postForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type)
```

```java
public Promise<RestResponseResult<Map>> postForm(String url, Map<String, String> params, Map<String, String> forms)
```

已POST方式发送请求，并且请求格式是 `application/x-www-form-urlencoded` 

`url` 请求地址

`headers` 请求头，可以为null

`params` 查询参数，可以为null

`forms` 表单参数，可以为null

`type` 返回值类型，默认是Map

PUT和DELETE方式与上面两种类似，这里就不过多介绍了。



[目录](./guide.md) [WebSocket的支持](./websocket.md) 上一节 下一节 [服务器参数配置](./serverconfig.md)