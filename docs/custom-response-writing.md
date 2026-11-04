# 定制：响应写出策略

控制器的返回值要经过一条固定管线才变成网络上的一串字节：先由结果转化环节装配为响应消息，再由响应消息写出器按消息类型路由写出到连接。2.6.x 起，装配规则与写出管线都固定在框架内，业务侧影响响应的入口是返回值本身；本文说明这条管线的每个环节、内置行为与可定制的边界。各类返回值的默认行为见[响应体系](./response.md)。

## 变更说明

2.5.x 的 `ResultConverterManager`（自定义结果转化器链）与 `ResponseWriterManager`（按响应类型选择写出器）均已下架，替换它们的是装配期固定的结果转化处理器与按消息类型路由的响应消息写出器。应用级自定义写出处理器的注册入口当前版本未开放——写出处理器契约仍在（见第 4 节），注册通道收口在框架装配内部。

## 1. 一次响应的加工过程

1. 中间件链与控制器产出返回值；已是响应消息（`HttpResponseMessage` 实例）的返回值直接透传，跳过第 2 步；
2. 结果转化处理器把非消息返回值装配为响应消息：装配规则固定——`String` 与 `null` 按 `text/html` 装配为字节消息，`byte[]` 原样作为响应体，`Publisher` 装配为流式响应消息，其余对象经 JSON 序列化装配；返回 `HttpResult` 时状态行与响应头取自值对象，正文装配口径相同；
3. 编排器为正常执行链产出的响应追加 `Set-Cookie`（`ctx.cookies()` 里的 Cookie 与会话 `JSESSIONID`），再按 CORS 配置写跨域响应头；
4. 响应消息写出器按消息类型路由到对应的写出处理器，把消息写入连接；
5. 同一条连接上的响应按序列定序写出，写出失败自动关闭通道。

错误响应（404、500 报错页，异常处理器的产出）走第 3 步之后的同一管线：同样带 CORS 头，同样参与定序。

## 2. 响应消息

`turboweb-http-core` 中的 `HttpResponseMessage<LINE, CONTENT>` 是响应侧的消息契约，状态行、响应头与响应体都在消息上。内置的具体形态：

| 消息 | 响应体 | 内置写出方 |
| --- | --- | --- |
| `ByteResponseMessage` | 字节数组 | 字节写出处理器 |
| `StreamResponseMessage` | `Flux<byte[]>` | 流式写出处理器 |
| `SseEmitter`（http-server 侧） | SSE 事件缓冲 | SSE 写出处理器 |

装配入口产出的字节消息与流式消息都是上面两种；业务侧可以直接构造消息作为返回值透传，但消息未声明 `Content-Type` 时写出侧按门禁降级写出报错页，不会自动补缺省类型——自己构造消息时，状态行、响应头与内容类型都要声明齐全。

## 3. 写出管线的内置行为

写出处理器执行前后的管线行为固定，对透传消息同样生效：

- **Content-Type 门禁**：消息未声明内容类型时不猜类型，降级写出 500 报错页；报错页继承原消息的非实体头（声明过的 Cookie、CORS 头等保留），实体头不继承。
- **字符集补写**：声明为 `text/*` 或 `application/json` 且缺 charset 段时补写缺省字符集；二进制类型不补。响应正文编码恒取 `HttpConfig` 的缺省字符集，与请求头声明无关。
- **同连接定序**：请求处理并发进行，响应写出按各自的序列号定序，前序未写完时后续响应排队，保证响应不交错；排队深度超过 `HttpConfig` 的 `maxInFlightResponses`（默认 8）时，视为该客户端消费过慢，框架关闭连接并释放积压请求。要放宽或收紧单连接并发，调这个参数。
- **背压**：流式写出以通道可写性驱动，通道来不及消费时上游暂停产出，恢复可写后续推；慢客户端不会把响应体堆在内存里。机制细节见[背压与事件卸载](./backpressure-offload.md)。
- **失败收尾**：写出中途失败（客户端断开、序列化错误等）自动关闭通道，不留半截响应。

## 4. 写出处理器契约

写出处理器按消息类型认领写出职责，契约如下：

```java
public interface MessageWriteHandler {

    enum CallbackType {
        FINISHED,
        IGNORE
    }

    @FunctionalInterface
    interface Callback {
        void onFinally(Throwable error, CallbackType type);
    }

    default void init(ResourceProvider resourceProvider) {
    }

    void write(HttpConnection connection, HttpResponseMessage<?> message, Callback callback);
}
```

- `init` 在装配期执行一次，`ResourceProvider` 提供框架资源（任务卸载执行器等内置资源）；
- `write` 把消息写出到给定连接。方法可以异步写出：发起写出后即返回，结果经回调上报；
- 回调必然且恰好被调用一次：以 `FINISHED` 报告本轮写出的正常结束（成功或失败，`error` 为 `null` 表示成功）；以 `IGNORE` 报告响应被忽略或连接属于长生命周期（如 SSE）两种无有限写出终态的情形，此时 `error` 恒为 `null`；
- 写出中途失败时，帧序已经残缺，处理器应在回调前先关闭通道，防止半截响应与后续响应在连接复用时交错——内置处理器都按此收尾；
- 处理器内部异常不向管线扩散，按上述失败路径上报。

框架内置的字节、流式、SSE 与降级报错页处理器都实现这个契约，装配时按消息类型绑定，构成第 1 节第 4 步的路由表。应用级注册入口当前版本未开放；需要改变写出内容时，业务侧可用的入口是返回值——声明 `HttpResult` 的状态行与响应头，或直接返回第 2 节的内置消息类型。

## 5. 请求侧的对应环节

写出前的请求侧加工（内容解析、参数绑定）不在本文范围，见[请求体系](./request.md)与[定制：参数解析器](./custom-parameter-parser.md)。

[首页](../README.md) | [响应体系](./response.md)
