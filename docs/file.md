# 文件的上传和下载

## 文件的上传

TurboWeb 的文件上传功能基于 Netty 的 FileUpload 组件实现，提供了简洁易用的 API 接口，方便开发者快速集成文件上传功能。

### 基本使用方法

**_单文件上传_**

通过`HttpContext`的`loadFile(...)`方法可获取上传的单个文件，返回`FileUpload`对象。示例代码如下：

```java
@Post("/upload")
public String upload01(HttpContext context) throws IOException {
    // 加载名为"file"的上传文件
    FileUpload fileUpload = context.loadFile("file");
    // 将文件重命名并保存为临时文件
    fileUpload.renameTo(File.createTempFile("testFile", ".tmp"));
    return "upload success";
}
```

**_多文件上传_**

当存在多个同名文件上传时，可使用`loadFiles(...)`方法批量获取文件列表，返回`List<FileUpload>`集合。示例代码如下：

```java
@Post("/upload")
public String upload01(HttpContext context) throws IOException {
    // 加载所有名为"file"的上传文件
    List<FileUpload> fileUploads = context.loadFiles("file");
    // 遍历文件列表并逐个保存
    fileUploads.forEach(fileUpload -> {
        try {
            // 使用UUID生成唯一文件名，避免冲突
            fileUpload.renameTo(File.createTempFile(UUID.randomUUID().toString(), ".tmp"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    });
    return "upload success";
}
```

> ⚠️ 重要提示：
> 在 TurboWeb 的实现中，为追求性能，将 Netty 的文件落盘阈值设置为了最大值。这意味着**即使是大文件，也会通过`FileUpload`的子类`MemoryFileUpload`以纯内存方式处理**，而非写入磁盘临时文件。
>
> 因此，强烈不建议使用 TurboWeb 上传大型文件，否则可能因内存占用过高导致 OOM（内存溢出）问题，影响应用稳定性。

**_文件大小的限制_**

TurboWeb 虽不直接提供文件大小限制的 API，但可通过设置请求体的最大长度（`ContentLength`）间接限制上传文件的大小。配置示例如下：

```java
// 启动服务器时配置最大请求体长度
BootStrapTurboWebServer.create()
        .http()
        .routerManager(routerManager)
        .and()
        .configServer(config -> {
            // 设置最大请求体长度为10MB（单位：字节）
            config.setMaxContentLength(1024 * 1024 * 10);
        })
        .start(8080); // 启动服务器并监听8080端口
```

上述配置中，`setMaxContentLength(...)`方法用于限制整个请求体的最大长度，当上传文件的总大小（含请求头信息）超过该值时，服务器会拒绝处理请求，从而起到限制文件大小的作用。

**_补充说明_**

对于需要上传大文件的场景，建议结合分布式文件存储服务（如 MinIO、阿里云 OSS 等）实现，并在前端进行分片上传处理。

`FileUpload`对象的`renameTo(...)`方法用于将内存中的文件数据写入磁盘，建议在调用该方法前验证文件类型、大小等信息，确保系统安全。

### 基于注解的方式实现文件下载

使用这种方式来获取上传的文件需要开启自动绑定的功能：

```java
AnnoRouterManager routerManager = new AnnoRouterManager(true);
```

获取单个上传的文件：

```java
@Post("/upload2")
public String upload02(@Upload("file") FileUpload file) {
    System.out.println(file);
    return "upload";
}
```

`@Upload` 告诉TurboWeb这里需要注入一个文件上传类型的对象，属性是文件名，形参类型需要是 `FileUpload`。

也可以接收多个上传的文件，使用 `List` 或者 `Set` 即可：

```java
@Post("/upload3")
public String upload03(@Upload("file") List<FileUpload> files) {
    files.forEach(System.out::println);
    return "upload";
}
```

## 文件的下载

在传统 Netty 中，零拷贝虽能显著提升文件传输性能，但其底层依赖的仍是**阻塞式系统调用**，会导致 Netty I/O 线程被长时间占用。
而在 TurboWeb 中，I/O 线程数量极少（默认甚至为单线程），这类阻塞会严重影响系统整体可用性。

> **旧方案：伪异步 I/O**
> 为了避免这一问题，TurboWeb 早期设计了**伪异步下载机制**：
>
> - 大文件被拆分为极小的数据块，每次发送一块后主动“让出执行权”，回到任务队列尾部。
> - 通过这种**让路机制**，确保重型文件传输不会拖慢普通请求的响应速度。
>
> **新方案：非阻塞零拷贝**
> 在新版本中，TurboWeb 对 Netty 的 `NioSocketChannel` 进行了增强，实现了**非阻塞的零拷贝传输**：
>
> - 发送过程交由独立的零拷贝线程池执行，不再阻塞 I/O 线程。
> - 后续写操作会被安全挂起，待零拷贝完成后自动恢复，避免线程等待。
> - 在保持高性能的同时，彻底解决了阻塞带来的可用性问题。

TurboWeb 提供四种文件下载方式，适用于不同场景：

- `HttpFileResult`：简单文件下载，适合小文件或内存数据
- `FileStreamResponse`：分块流式下载，支持背压机制，适合大文件（仅能下载磁盘文件）
- `AsyncFileResponse`：基于操作系统 AIO 的异步下载，适合支持 AIO 的环境（仅能下载磁盘文件）
- `ZeroCopyResponse` : 基于零拷贝的方式进行文件下载，非常适合大文件的下载（仅能下载磁盘文件）

### HttpFileResult

`HttpFileResult` 是最简单的文件下载方式，直接通过文件路径或内存字节数组生成下载响应，适用于**小文件**或**内存中的数据**。

**_下载磁盘文件_**

通过文件路径创建下载响应：

```java
@Get("/download1")
public HttpFileResult download01(HttpContext context) {
    File file = new File("E:\\tmp\\logo.png"); // 指定文件路径
    return HttpFileResult.file(file); // 生成文件下载响应
}
```

**_下载内存字节数组_**

直接将内存中的字节数组作为下载内容：

```java
@Get("/download1")
public HttpFileResult download02(HttpContext context) {
    byte[] buffer = getFileBytesFromMemory(); // 从内存获取字节数组
    return HttpFileResult.bytes(buffer); // 基于字节数组生成下载响应
}
```

**_直接在浏览器展示内容_**

若需浏览器直接展示文件（而非下载），可使用重载方法指定文件类型：

```java
// 直接在浏览器展示PNG图片
return HttpFileResult.png(buffer); 
// 直接在浏览器展示JPEG图片
return HttpFileResult.jpeg(buffer); 
// 直接在浏览器展示GIF图片
return HttpFileResult.gif(buffer); 
// 直接在浏览器展示MP4视频
return HttpFileResult.mp4(buffer); 
```

**_核心特点_**

优势：API 简洁，无需手动处理流，适合快速实现小文件下载。

限制：会将文件**一次性全部读入内存**，因此**不适合大文件下载**，否则可能导致 OOM（内存溢出）。

### FileStreamResponse

`FileStreamResponse` 基于分块流式下载实现，通过 `FileStream` 接口的实现类（默认 `BackPressFileStream`）支持**背压机制**，避免一次性加载大文件到内存，适合**大文件下载**。

**_基础用法_**

直接通过文件路径创建流式下载响应：

```java
@Get("/download2")
public HttpResponse download02(HttpContext context) {
    File file = new File("E:\\tmp\\large_file.zip"); // 大文件路径
    return new FileStreamResponse(file); // 默认开启背压，分块下载
}
```

**_自定义分块大小和背压设置_**

可手动指定分块大小（默认 2MB）和是否开启背压：

```java
// 关闭背压（仅为建议，TurboWeb可能自动开启）
new FileStreamResponse(file, false); 

// 指定分块大小为8KB，关闭背压（分块大小单位：字节）
new FileStreamResponse(file, 8192, false); 
```

**_背压机制_**

背压（Backpressure）是一种流量控制机制，通过分块读取文件并按需发送，避免因读取速度过快导致内存堆积。TurboWeb 默认开启背压，且会**自动判断文件大小**：若文件大小超过操作系统空闲内存的一半，即使手动关闭背压，TurboWeb 也会强制开启，防止 OOM。

**_实现原理_**

`BackPressFileStream` 采用 “协作式伪异步 IO” 模式：

1. 下载任务到达 `HttpScheduler` 后，从虚拟线程切换到专门的磁盘读取线程（少量平台线程）。
2. 磁盘线程分块读取文件（如先读 A 文件的一个分块），交给 Netty IO 线程发送。
3. 无需等待发送完成，继续读取下一个文件的分块（如 B 文件的分块），通过轮询实现多文件并发下载，避免阻塞。

**_分块大小建议_**

**不宜过大**：避免单块内存占用过高导致 OOM。

**不宜过小**：过小会增加 IO 中断频率和上下文切换，加重 CPU 负担。

默认 2MB 分块大小适用于多数场景，可根据文件类型微调（如小文件可设为 8KB）。

### AsyncFileResponse

`AsyncFileResponse` 基于**操作系统 AIO（异步 IO）** 实现，真正的异步文件读取，适用于**支持 AIO 的操作系统**，性能优于同步读取。

**_基础用法_**

直接通过文件路径创建异步下载响应：

```java
@Get("/download3")
public HttpResponse download03(HttpContext context) {
    File file = new File("E:\\tmp\\large_file.zip"); // 大文件路径
    return new AsyncFileResponse(file); // 基于AIO的异步下载
}
```

**_自定义分块大小_**

默认分块大小为 8KB，可手动指定：

```java
// 指定分块大小为16KB（单位：字节）
new AsyncFileResponse(file, 16384); 
```

**_依赖操作系统 AIO 支持_**

若操作系统支持 AIO，`AsyncFileResponse` 能通过异步 IO 提升下载性能。

若操作系统不支持 AIO（如部分老旧系统），建议使用 `FileStreamResponse`，否则可能因 AIO 模拟导致性能下降。

**_实现原理_**

与 `BackPressFileStream` 类似，采用协作式异步模式：

1. 下载任务到达 `HttpScheduler` 后，触发操作系统 AIO 读取请求（非阻塞）。
2. 操作系统完成文件读取后通过回调通知 TurboWeb，再将数据交给 Netty IO 线程发送。
3. 发送完成后自动触发下一分块的 AIO 读取，实现无阻塞的流式下载。

### ZeroCopyResponse

`ZeroCopyResponse` 采用零拷贝的方式进行文件下载，性能非常高。

```java
@Get("/download4")
public HttpResponse download04(HttpContext context) {
    File file = new File("E:\\tmp\\logo.png");
    return new ZeroCopyResponse(file);
}
```



[首页](../README.md) | [响应数据的处理](./response.md) | [异常处理器](./exceptionhandler.md)