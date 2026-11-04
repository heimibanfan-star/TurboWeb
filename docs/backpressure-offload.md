# 背压与事件卸载

TurboWeb 的吞吐模型由两件事撑起：业务执行不在 Netty 的 IO 线程上跑，而是卸载给微任务执行器；响应写出不靠堆缓冲硬扛，而是由背压闸门跟着消费端的节奏走。本文以事件卸载为主线展开——微任务执行器承载什么、默认实现是什么、怎么整体替换——背压作为写出侧的配套机制放在最后。

## 变更说明

2.6.x 起，请求与 WebSocket 消息的执行统一收口到微任务执行器（`MicroTaskExecutor`）的虚拟线程实现，旧的调度器线程池下架；流式写出引入背压闭环，写出通道来不及消费时上游暂停产出。旧的磁盘 IO 线程池三参数随之没有存在必要，已下架。

## 1. 事件卸载：微任务执行器

### 1.1 为什么要卸载

```text
Netty IO 线程                    微任务执行器
    │                                  │
    │  请求入站、装配消息                │
    ├──────── 提交编排任务 ────────────→│  中间件链 → 控制器
    │                                  │  内容解析、参数绑定
    │                                  │  装配响应消息
    │  响应写出（凭据链推进）             │
    │←─────── 消息交写出管线 ────────────┤
    │  按可写性与定序写到对端             │
```

IO 线程只做入站搬运与最终的字节写出，中间件、控制器、内容解析这些业务环节全部交给执行器承载。因此业务代码可以放心写同步阻塞调用——数据库查询、HTTP 调用、文件读写都直接写，阻塞的只是承载当前任务的那条线程，不占用 IO 线程。

### 1.2 两个执行器槽位

应用装配（`HttpServerRuntime.Builder#build()`）登记两个执行器资源，职责与替换方式不同：

| 槽位 | 资源名 | 承载的任务 | 默认实现 | 替换方式 |
| --- | --- | --- | --- | --- |
| 请求微任务执行器 | `microTaskExecutor` | 请求编排任务：中间件链、控制器、内容解析与参数绑定 | `VirtualThreadMicroTaskExecutor`，按 `HttpConfig` 装配 | 不可整体替换，经 `HttpConfig` 的调度限流四参数调整行为 |
| 任务卸载执行器 | `taskOffloadThreadPool` | 流式响应写出凭据的完成任务，使续流脱离写出落定线程 | `ElasticMicroTaskExecutor.of("task-offload-micro-task")` | `HttpServerRuntime.builder().taskOffloadExecutor(...)` 整体替换 |

### 1.3 请求微任务执行器

每个请求经编排器提交到请求微任务执行器，装配口径由 `HttpConfig` 决定：

- **默认（限流关闭）**：非受限虚拟线程实现。每个请求进入一个新建虚拟线程执行，不做并发限制，也不会拒收。
- **开启调度限流**：`enableHttpSchedulerLimit(true)` 后改为受限实现，以固定资源数约束同时执行的请求量；资源用尽时请求先在虚拟线程里挂起排队，排队名额或超时耗尽则提交不被受理，编排器直接写出 429，正文为 `too many request`（`text/plain;charset=utf-8`）。429 不经过中间件，也不进异常处理器。四个参数的含义与取值约束见[框架参数调整](./config.md)的调度限流一节。

### 1.4 任务卸载执行器

流式响应的写出按凭据链推进，凭据的完成任务交给任务卸载执行器调度：首部与每块响应体的写出凭据在它上面兑现，续流因此脱离写出落定线程，不必占住一条线程等网络。这是纯粹的短小、高频任务，与默认实现的形态匹配。

默认实现是弹性微任务执行器（`ElasticMicroTaskExecutor`）：一个小型平台线程池（核心线程允许超时回收，空闲可回落到 0）前置一层缓冲队列，池满时任务先进缓冲，由一条虚拟搬运线程带退避地把缓冲任务搬进池；关闭时经 `close()` 释放。容量按 CPU 核数派生——缓冲队列 4 倍核数、核心队列减半、最大线程数 2 倍核数——也可以用 `of(threadName, cacheQueue, coreQueue, maxThreadNum)` 显式指定。

**替换写法**：

```java
HttpServerRuntime runtime = HttpServerRuntime.builder()
        .taskOffloadExecutor(VirtualThreadMicroTaskExecutor.ofUnlimited("offload-micro-task"))
        .routeRegister(routeRegister)
        .build();
```

- 传 `null` 抛 `NullPointerException`；传入的执行器原样登记为内置资源，由框架随应用关闭统一 `close()`——默认契约的 `close()` 是空实现，持有资源的实现应在其中释放；
- 注册后调度限流四参数不受影响，它们只作用于请求微任务执行器；
- WebSocket 侧的执行器在 `WebSocketInitChannelHandler` 的建造者上单独注入，见[WebSocket](./websocket.md)。

### 1.5 替换时的契约与选型

自定义执行器实现 `MicroTaskExecutor` 接口。替换前先核对这个提交契约：

```java
@FunctionalInterface
public interface MicroTaskExecutor extends AutoCloseable {

    void submit(Runnable task, FinallyHandler finallyHandler);

    enum FinallyType { EXECUTED, NOT_ACCEPT }

    record FinallySignal(CallbackThread callbackThread, FinallyType finallyType) {
        public enum CallbackThread { SUBMITTER, WORKER }
    }

    @FunctionalInterface
    interface FinallyHandler {
        void onFinally(FinallySignal finallySignal);
    }

    @Override
    default void close() {
    }
}
```

- `task` 与 `finallyHandler` 都不为 `null`，参数校验先于任何资源动作发生；
- 收尾回调保证被调用且每次提交仅调用一次：任务执行完毕（正常或抛异常）以 `EXECUTED` 收尾；未取得资源、线程启动失败或排队等待被放弃以 `NOT_ACCEPT` 收尾——请求侧收到 `NOT_ACCEPT` 即写 429，因此不受理必须经回调上报，不能静默丢弃任务；
- 信号携带回调发生的线程方：`SUBMITTER` 表示回调在提交线程同步触发（提交返回前），`WORKER` 表示在承载任务的线程异步触发；
- 调用方无法凭句柄中断或等待任务，只能以收尾回调感知结果。

框架内置三套实现，替换时优先从中选，而不是裸线程池——收尾契约要自己兜对：

| 实现 | 工厂 | 形态 | 适用 |
| --- | --- | --- | --- |
| `VirtualThreadMicroTaskExecutor` | `ofUnlimited(threadName)` / `ofLimited(resourceCount, maxSuspendThreadNum, timeout, threadName, fair)` | 每任务一个新虚拟线程；受限版以资源数约束并发，支持挂起排队与超时 | 要并发上限或排队语义时选 `ofLimited` |
| `ElasticMicroTaskExecutor` | `of(threadName)` / `of(threadName, cacheQueue, coreQueue, maxThreadNum)` | 平台线程池加缓冲队列，虚拟搬运线程搬任务，空闲回落到 0 | 凭据类短任务，不想为每个任务开虚拟线程 |
| `ForkJoinMicroTaskExecutor` | `ofShared()` / `ofIsolated(threadName[, threadCount])` | 工作窃取池，共享单例或独立池 | CPU 型任务；WebSocket 文档用它作演示 |

## 2. 背压：写出侧的三道闸门

事件卸载解决"业务不占 IO 线程"，背压解决"写出不超前于对端的消费能力"。三道闸门都在框架内固定，无开关：

- **流式写出，在途窗口为 1**：响应头写出后才订阅流，上一块的写出凭据完成后才向流要下一块。通道来不及消费时数据被管道暂扣、凭据不完成，上游随即停住，内存里同时滞留的只有一个字节块。任一环节失败则取消流、关闭通道。
- **写出节流，跟随通道可写性**：每条连接的管线里有节流处理器，通道不可写期间按到达次序暂扣出站消息与冲刷事件，可写恢复时按序放行并统一冲刷；对所有出站消息（含 WebSocket 帧）同口径生效。
- **同连接定序与在途上限**：同一连接上的响应按序列号定序写出，排队深度超过 `maxInFlightResponses`（默认 8）时视为客户端消费过慢，关闭连接并释放积压请求。

SSE 是长生命周期响应的特例：事件在写出开始前进内部缓冲，缓冲上限默认 32 条，溢出抛 `SseBufferException`，容量可在构造 `SseEmitter` 时指定；连接建立后事件随发随写，不受在途上限约束。

参数速查：

| 机制 | 参数或入口 | 默认 |
| --- | --- | --- |
| 单连接在途响应上限 | `HttpConfig.builder().maxInFlightResponses(n)` | `8` |
| 调度限流开关 | `HttpConfig.builder().enableHttpSchedulerLimit(b)` | `false` |
| 同时执行的请求上限 | `httpSchedulerLimitCount(n)` | `10000` |
| 排队名额 | `httpSchedulerLimitCacheThread(n)` | `2000` |
| 排队超时 | `httpSchedulerLimitTimeout(ms)` | `500` |
| 任务卸载执行器整体替换 | `HttpServerRuntime.builder().taskOffloadExecutor(executor)` | `ElasticMicroTaskExecutor.of("task-offload-micro-task")` |
| SSE 事件缓冲 | `new SseEmitter(capacity, ...)` | `32` |

调度限流与在途上限各管一头：一个约束全服务同时执行的请求量，一个约束单条连接的积压深度；背压节流与在途窗口为 1 则保证每条连接的写出不超前于对端的消费能力。

[首页](../README.md) | [响应体系](./response.md) | [框架参数调整](./config.md) | [WebSocket](./websocket.md)
