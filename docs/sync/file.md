# <img src="../image/logo.png"/>

# 文件的处理

TurboWeb 框架支持文件的**上传**、**下载**与**本地存储**，并针对高并发场景下的**大文件传输**问题，提供了一系列优化策略，确保系统运行稳定、内存开销可控，防止因不当处理大文件而引发 OOM（OutOfMemoryError）异常。

## 文件的上传

TurboWeb 框架的文件上传功能直接基于 Netty 的上传处理机制实现。Netty 在处理大文件上传时，采用了自动落盘（写临时文件）的策略，有效避免了将大文件完全加载到堆内存中，从而防止了因文件过大导致的 OOM（内存溢出）问题。

因此，TurboWeb 并未对文件上传做额外的专门优化，而是充分利用了 Netty 的底层能力，保证上传过程的稳定性和高效性。同时，TurboWeb 提供了简洁的接口方便开发者直接操作上传文件的流和元数据，确保上传功能的易用性和可控性。

接下来看一下TurboWeb如何实现文件的上传：

```java
@Post("/upload01")
public String upload01(HttpContext c) throws IOException {
    FileUpload fileUpload = c.loadFile("file");
    System.out.println(fileUpload);
    fileUpload.renameTo(File.createTempFile("HeiMi", ".tmp"));
    return "upload01";
}
```

之后定义一个form表单进行文件的上传：

```html
<form action="http://localhost:8080/upload01" method="post" enctype="multipart/form-data">
    <input type="file" name="file"></input>
    <input type="submit"></input>
</form>
```

可以看一下打印的信息：

```text
Mixed: content-disposition: form-data; name="file"; filename="sqlite-tools-win-x64-3490200.zip"
content-type: application/x-zip-compressed; charset=UTF-8
content-length: 6422627
Completed: true
IsInMemory: false
RealFile: C:\Users\heimi\AppData\Local\Temp\FUp_2568822843930813806_-838595071 DeleteAfter: false
```

TurboWeb通过 ``HttpContext`` 的 ``loadFile(...)`` 方法，从请求中提取名为 ``"file"`` 的上传文件。

- 这里的 `FileUpload` 实际上是有Netty底层自动封装实现的。
- Netty 会自动判断上传文件大小，若文件较大，则自动写入临时文件（落盘），防止文件内容全部加载到内存。

``fileUpload.renameTo(File.createTempFile("HeiMi", ".tmp"));`` 将临时文件重命名移动到一个新的临时文件。

由于文件本身已经是落盘的，`renameTo` 只是修改文件路径，不涉及内存数据的拷贝，避免了大文件读写导致的内存压力。

在实际业务中，前端有时会通过多个相同 `name` 属性的 `<input type="file" name="file">` 上传多个文件。TurboWeb 针对此场景也提供了对应的接收方案。

示例中，`HttpContext` 的 `loadFiles("file")` 方法会返回一个 `List<FileUpload>`，包含所有名为 `"file"` 的上传文件。

```java
@Post("/upload02")
public String upload02(HttpContext c) throws IOException {
    List<FileUpload> fileUploads = c.loadFiles("file");
    for (FileUpload fileUpload : fileUploads) {
        System.out.println(fileUpload);
        fileUpload.renameTo(File.createTempFile("HeiMi", ".tmp"));
    }
    return "upload02";
}
```

对应的前端表单允许用户选择多个文件上传：

```html
<form action="http://localhost:8080/upload02" method="post" enctype="multipart/form-data">
    <input type="file" name="file"></input>
    <input type="file" name="file"></input>
    <input type="submit"></input>
</form>
```

底层同样依赖于 Netty 的上传机制，所有上传文件均会根据大小决定是否落盘，确保不会因大文件上传导致内存溢出。开发者只需通过简单的列表遍历即可处理多个文件，极大简化了多文件上传的业务逻辑。

## 文件的下载

TurboWeb 框架支持文件下载功能，提供了两种主要实现方式：通过 ``HttpContext`` 的API进行文件下载、使用FileStreamResponse。

###  通过 `HttpContext` 方法直接下载文件

`HttpContext` 内置了文件下载相关的简便方法，开发者可以快速将本地文件或资源发送给客户端。此方式适合一般文件下载需求，操作简单直接。

此外，`HttpContext` 提供了丰富的文件助手工具（如图片文件预览、内容类型自动识别等），方便开发者基于文件下载实现更灵活的业务功能，比如可以直接将图片返回给浏览器直接显示。

```java
@Get("/download01")
public void download01(HttpContext c) {
    File file = new File("E:\\tmp\\turbo.hprof");
    c.download(file);
}
```

``HttpContext`` 的 ``download(...)`` 方法提供了很多的重载，方法签名如下：

```java
Void download(HttpResponseStatus status, byte[] bytes, String filename);
```

**用途**：直接将内存中的字节数组作为文件内容下载，并指定下载的文件名和状态码。

```java
Void download(byte[] bytes, String filename);
```

**用途**：直接将内存中的字节数组作为文件内容下载，并指定下载的文件名。

```java
Void download(HttpResponseStatus status, File file);
```

**用途**：下载本地文件，同时自定义响应状态码。

```java
Void download(File file);
```

**用途**：下载本地文件，自动设置文件名和 MIME 类型。

```java
Void download(HttpResponseStatus status, InputStream inputStream, String filename);
```

**用途**：通过输入流返回文件内容，适合从其他存储介质（如数据库、远程存储）读取数据，并返回状态码。

```java
Void download(InputStream inputStream, String filename);
```

**用途**：通过输入流返回文件内容，适合从其他存储介质（如数据库、远程存储）读取数据。

#### ``HttpContext`` 的文件助手

`HttpContext` 提供了**文件助手**（File Helper）功能，用于扩展文件处理能力，如图片预览等场景，简化了内容类型设置和响应构建逻辑。

**文件助手的设计理念**

- **按需创建**：默认情况下，`HttpContext` 并不会创建文件助手实例。只有当用户首次调用 `c.fileHelper()` 时，系统才会懒加载构造 `HttpContextFileHelper` 实例；
- **内部封装但不冗余**：文件助手内部封装了常见文件展示逻辑，但所有行为最终仍委托给 `HttpContext` 执行，零冗余、零性能损耗；
- **操作便捷**：文件助手支持按文件类型命名的便捷方法（如 `png(...)`、`jpg(...)`、`txt(...)` 等），代码表达直观且可读性强。

以让浏览器打开一个图片为例子：

```java
@Get("/download02")
public void download02(HttpContext c) throws FileNotFoundException {
    HttpContextFileHelper helper = c.fileHelper();
    helper.png(new FileInputStream("E:\\tmp\\logo.png"));
}
```

**那么文件助手的原理是什么呢？**

- 所有 `helper.xxx(...)` 方法内部其实是调用了 `HttpContext.download(...)`，统一处理响应头设置、内容流写入、MIME 类型识别等工作。
- 因此你也可以理解文件助手为 `HttpContext` 的一个语法糖扩展，让常见场景变得更简单、更优雅。

###  通过 `FileStreamResponse` 实现背压控制的流式下载

针对大文件或需要控制传输速率的场景，TurboWeb 提供了基于 `FileStreamResponse` 的流式文件下载方案。

该方式采用背压机制（backpressure）控制数据流出速率，避免一次性加载过大文件导致内存压力过高，从而有效防止OOM问题。同时支持边读边传输，提升传输效率和响应速度。

**Netty支持零拷贝，但是为什么TurboWeb不采用Netty的零拷贝？**

虽然 Netty 支持 **零拷贝（Zero-Copy）**，可通过 `DefaultFileRegion` 直接在内核空间完成文件传输，节省用户空间到内核空间的数据拷贝，降低 CPU 占用和内存复制开销。但在 TurboWeb 的流式下载场景中并未采用该机制，主要出于以下考虑：

- **IO 线程数量少且非常关键**：TurboWeb 的 IO 线程设计非常轻量，一个实例通常只维护少量 IO 线程，用于高频网络事件的处理。一旦阻塞，将直接影响整个服务的吞吐和响应能力。
- **Netty 的零拷贝会阻塞 IO 线程**：零拷贝虽然绕过了内存复制，但其 `FileRegion` 的底层调用仍依赖 IO 线程等待传输完成，尤其在大文件传输期间，IO 线程可能长时间无法释放，从而拖慢所有连接的处理。
- **TurboWeb 采用自定义的分块传输方案**：为了解决 IO 线程阻塞问题，TurboWeb 设计了基于 **直接内存** 的自定义分块传输逻辑。通过 `FileStreamResponse`，将大文件分块读取，并由业务线程（通常是虚拟线程）写出。这种方式避免了 Netty 零拷贝对 IO 线程的阻塞；降低了堆内存使用，缓解 GC 压力；即便发生阻塞，也仅阻塞业务线程，**不会影响 Netty 的 IO 线程调度**。

接下来用一个例子来展示文件流的下载方式：

```java
@Get("/download03")
public HttpResponse download03(HttpContext c) {
    File file = new File("E:\\tmp\\turbo.hprof");
    return new FileStreamResponse(file, 8192, true);
}
```

此代码实现了一个大文件的下载接口，TurboWeb 会自动将文件**切分成多个小块**，每次读取 8192 字节并发送给客户端，直到文件传输完毕。

```java
public FileStreamResponse(File file, int chunkSize, boolean backPress)
```

构造器参数说明：

``file``：下载的文件对象，必须存在且具有读取权限。

``chunkSize``：每次读取并写入响应的数据块大小（单位：字节）。建议设置为 4KB - 64KB。

``backPress``：是否启用背压机制。建议开启（`true`），可避免客户端接收过慢导致内存堆积。

该对象还有其余的简化构造器，简化的构造器中缺少的参数都会按照默认值配置：

``chunkSize``：默认是8192。

``backPress``：默认是true。

``filenameCharset``：默认是``StandardCharsets.UTF_8``

``version``：默认是``HttpVersion.HTTP_1_1``

**为什么要使用背压？——TurboWeb 中的文件流“节奏控制”**

在 TurboWeb 中，背压机制是一种**对数据传输速率进行主动调节的策略**，避免业务线程和 IO 线程之间出现“生产过快、消费过慢”的失衡问题，从而防止内存堆积、吞吐下降或系统不稳定。

**不启用背压的风险**

如果在文件传输中未启用背压，业务线程会持续不断地将文件分块提交给 IO 线程处理。对于小文件，这种策略可以快速完成传输，但对于大文件或高并发下载场景，则可能引发以下问题：

- **内存堆积**：即便每个分块仅为 8KB，数以万计的块堆积在内存中，仍可能导致直接内存或堆内存溢出。
- **任务队列阻塞**：Netty 的 IO 线程因处理大量分块任务而被占满，普通请求无法及时调度。
- **请求响应延迟**：其他请求被文件任务“饿死”，影响系统整体吞吐。

**启用背压的调度策略**

启用背压机制后，TurboWeb 会采用“**处理一个再给下一个**”的策略进行文件分块传输

1. **业务线程** 读取一个分块并提交；
2. 提交后**立即挂起等待 IO 线程处理完成**；
3. 当 IO 线程发送完该分块后，**主动唤醒业务线程**；
4. 业务线程继续读取并提交下一个分块。

整个过程始终保持“**只有一个待发送分块存在于系统中**”，不会堆积。

**类比理解：一个个排队进门**

背压机制可以理解为：“我不是一下子把所有任务都交给你，而是等你处理完一个，再告诉你处理下一个。”

> 就像你去银行办理业务，不是一下子把所有客户都推给前台，而是一个个来。每处理完一个，就重新排队，让其它客户也有机会插入，保持业务公平有序。

## 文件下载选择HttpContext还是FileStreamResponse呢？

在 TurboWeb 中，针对不同来源的文件资源，我们建议根据实际场景选择合适的下载方式：

**推荐使用 `FileStreamResponse` 的场景：**

如果**文件位于磁盘**中（即 `File` 对象），推荐使用 `FileStreamResponse`：

- 利用 **直接内存**（Direct Buffer）进行分块传输，**不经过 JVM 堆内存**，有效降低 GC 压力；
- 支持按需开启 **背压机制**，提升系统稳定性和并发下载能力；
- 更适合处理 **大文件下载** 或 **高并发场景**。

**推荐使用 `HttpContext.download(...)` 的场景：**

如果**文件内容已经在内存中**（如字节数组、InputStream），则推荐使用 `HttpContext` 提供的下载方法：

- 提供多个重载形式，**支持 byte[]、InputStream、File 等多种类型**；
- 可快速设置文件名、响应状态码等；
- 内置**内容类型识别、图片直接预览**等高级功能；
- 更适合处理**业务中动态生成的小文件**或**内存资源**的导出场景。



[目录](./guide.md) [路由的支持](./router.md) 上一节 下一节 [异常处理器](./exceptionhandler.md)