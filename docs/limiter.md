# 三级限流保护体系

为防止海量请求瞬间冲垮系统，TurboWeb 构建了 三级限流防护体系：

1. 最大连接数限制（第一道防线）
2. 业务并发数限制（第二道防线）
3. 请求速率限制（第三道防线）

该体系从连接接入、请求调度、业务路径三方面分层防护，确保系统在极端高并发场景下依然稳定可用。

## 一级限流 — 最大连接数控制

一级限流直接作用于**TCP 连接数量**，是系统的第一道防线。
 当连接数达到阈值时，TurboWeb 会立即拒绝新的连接请求。

```java
BootStrapTurboWebServer.create()
        .configServer(config -> {
            // 设置最大连接数
            config.setMaxConnections(5000);
        })
        .start();
```

内部会根据 I/O 线程数 与 CPU 核心数 自动选择最优的并发控制策略：

- 单 I/O 线程：无锁统计连接数，性能极高。
- I/O 线程 ≤ CPU 核心数：使用 CAS 无锁方式进行并发控制。
- I/O 线程 > CPU 核心数：使用排他锁保证安全。

如 CPU 核心数与操作系统报告值不一致，可手动指定：

```java
BootStrapTurboWebServer.create()
        .configServer(config -> {
            config.setMaxConnections(5000);
            config.setCpuNum(8); // 手动设置 CPU 核心数
        })
        .start();
```

> 默认会读取操作系统的 CPU 核心数。

## 二级限流 — 调度器并发数限制

二级限流作用于 HTTP 请求调度阶段，由 HttpScheduler 实现，通过许可池限制业务执行的最大并发数。

- 有许可 → 直接进入业务执行。
- 无许可且挂起数未达上限 → 创建虚拟线程，等待许可释放（超时也直接拒绝）。
- 无许可且挂起数已达上限 → 直接拒绝请求。

```java
BootStrapTurboWebServer.create()
        .configServer(config -> {
            config.setEnableHttpSchedulerLimit(true);     // 开启调度器限流
            config.setHttpSchedulerLimitCount(1000);      // 最大并发数
            config.setHttpSchedulerLimitCacheThread(2000);// 最大挂起线程数
            config.setHttpSchedulerLimitTimeout(1000);    // 挂起超时时间(ms)
        })
        .start();
```

> 设计理念：在挂起数未达上限时，系统认为请求仍有较大概率获取到许可，因此先挂起等待，以降低直接拒绝的概率；一旦挂起数到达上限，请求获批概率极低，因此直接拒绝，保护系统稳定性。

## 三级限流 — 中间件速率控制

三级限流基于 中间件机制，可按路径精确控制请求速率，适合精细化业务限流。
与前两级相比，它的拦截时机更靠后，但粒度更灵活。

```java
// 创建路径限流器
PathLimiter pathLimiter = new PathLimiter();
// 规则：/user/** 每秒最多允许 10 次请求
pathLimiter.addRule("/user/**", 10, 1);

BootStrapTurboWebServer.create()
        .http().middleware(pathLimiter) // 注册限流器
        .and().start();
```

以上配置意味着：以 `/user` 开头的所有请求，每秒最多处理 10 次，超出则直接拒绝。



[首页](../README.md) | [服务器参数配置](./config.md)

