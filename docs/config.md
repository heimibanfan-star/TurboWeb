# 服务器参数配置

TurboWeb 提供丰富的服务器参数配置选项，开发者可以根据实际业务需求灵活调整，以获得更优的性能与资源利用率。

## 线程模型与配置

TurboWeb的线程模型有如下几部分组成：

TurboWeb 的线程架构由以下几类线程组成：

- **Accept 线程**：用于接收客户端连接，仅一个线程，系统自动管理，不可配置。
- **IO 线程**：负责网络事件的读取与分发，默认为单线程，可自定义线程数。
- **业务线程（工作线程）**：
  - 普通 HTTP 请求：基于 JDK 虚拟线程（Loom）调度。
  - WebSocket：默认使用虚拟线程，可切换为 ForkJoin 线程池。
- **磁盘读写线程**：用于执行文件读取等磁盘 I/O 操作，适用于 `FileStreamResponse` 等场景，可独立配置线程池参数。

### 配置IO线程的数量

使用有参的 `create(..)` 方法可自定义 IO 线程数量：

```java
BootStrapTurboWebServer.create(8)
```

上述示例将 IO 线程设置为 8 个。默认仅启用 1 个线程。

> 推荐：
>
> IO线程尽量不要配置的太多，因为IO线程只做事件的分发，不参与复杂逻辑的运算，最好不要超过CPU核心的一半。

### 磁盘读取线程的配置

磁盘读取线程池适用于大文件下载、分块读取等磁盘密集型场景：

```
java


复制编辑
```

```java
BootStrapTurboWebServer.create()
        .configServer(config -> {
            // 设置核心队列大小，默认一个线程运行，到达核心队列大小开始扩容线程
            config.setDiskOpeThreadCoreQueue(2);
            // 当核心队列满了，并且线程创建到最大值，再次提交的任务到达缓冲队列
            config.setDiskOpeThreadCacheQueue(1024);
            // 设置线程的最大数量
            config.setDiskOpeThreadMaxThreadNum(10);
        }).start();
```

| 参数                           | 描述                                                         |
| ------------------------------ | ------------------------------------------------------------ |
| `setDiskOpeThreadCoreQueue`    | 当线程数处于核心范围内时任务队列的最大容量，超出将扩容线程数 |
| `setDiskOpeThreadCacheQueue`   | 线程数量达到最大后用于缓冲的队列                             |
| `setDiskOpeThreadMaxThreadNum` | 磁盘线程池可创建的最大线程数                                 |

## 通用配置接口：`configServer(..)`

大部分服务端参数均可通过 `configServer(..)` 提供的回调接口进行集中配置：

```java
BootStrapTurboWebServer.create()
        .configServer(config -> {
            // 设置请求体的大小
            config.setMaxContentLength(1024 * 1024 * 10);
            // 显示请求日志
            config.setShowRequestLog(true);
            // ....
        }).start();
```

可配置项涵盖请求体大小限制、日志开关、磁盘线程参数、安全设置等，详见源码注释。



[首页](../README.md) | [监听器](./listener.md) | [三级限流保护体系](./limiter.md)