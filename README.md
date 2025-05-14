# Turbo-web使用手册
> 版本 = Ox.0.1

## 升级内容
### 新增功能
> 1.二级限流策略中间件对服务器的流量控制。
>
> 2.CORS中间件。
>
> 3.对服务器实例信息收集的中间件。

### 原有功能的优化
> 1.完善对websocket的支持。
>
> 2.对路由调用的优化（将反射调用替换为方法句柄调用）。

## 简介
### 项目概述
> 该项目是一个基于 Netty 的高性能 Web 框架，旨在为开发者提供高效、简洁的并发处理能力。通过采用 虚拟线程 技术，框架允许开发者像编写传统的阻塞代码一样编写并发逻辑，而无需关注复杂的线程管理和异步编程细节。框架内核基于 Netty，处理低级的 I/O 事件（如 accept 和 read），并将业务逻辑分发到虚拟线程中执行，从而实现极高的并发性能和较低的资源消耗。
> 
> 框架采用了 MVC 架构，通过 Context 对象来统一管理请求与响应，支持强大的中间件机制与灵活的异常处理，旨在为开发者提供现代 Web 开发的高效工具。

### 主要特性
- 虚拟线程支持：框架充分利用 JDK 21 的虚拟线程技术，使得开发者能够像编写传统的阻塞代码一样处理并发请求，而无需关心线程池管理和异步回调，极大简化了并发编程的复杂度。
- 基于 Netty 的高效事件驱动模型：框架依托 Netty 处理网络 I/O 事件，确保高效的请求接收与数据读取，最大限度减少资源消耗。
- MVC 架构：框架遵循 MVC 模式，提供清晰的应用组织方式，通过 Context 对象在控制器和视图之间传递数据，并为业务逻辑提供强大的支持。
- 中间件机制：通过 Middleware，开发者可以在请求处理过程中灵活插入日志记录、认证、性能监控等功能，提升系统的可扩展性。
- 异常处理：框架的异常处理方式与 Java 标准异常处理机制一致，便于开发者实现定制化的错误处理逻辑，确保系统的稳定性和可维护性。

### 设计理念
- 简化并发编程：框架的核心目标是让开发者能够像编写传统的阻塞代码一样处理并发请求，而不必陷入异步编程和线程管理的复杂性。通过 虚拟线程 技术，开发者可以轻松利用多核 CPU 提高性能，同时避免传统线程池的资源开销。
- 高效的事件驱动模型：框架采用 Netty 的事件驱动机制，保证在高并发场景下仍能提供卓越的网络性能，同时使得 I/O 操作与业务逻辑执行解耦，提升系统的响应速度和吞吐量。
- 灵活且可扩展的架构：通过 Middleware 和灵活的路由机制，框架使得开发者可以方便地扩展功能，插入自定义中间件和处理器，实现业务逻辑与基础设施的分离。
- 一致的异常处理：框架采用与 Java 标准异常机制一致的异常处理方式，简化了错误捕获与处理的流程，使开发者可以更专注于业务逻辑的实现。

## 安装
### 安装到maven仓库
下载好对应的jar包之后在当前jar包的目录执行如下命令：
```bash
mvn install:install-file -Dfile=turbo-web-xxx.jar \
                         -DgroupId=org.turbo \
                         -DartifactId=turbo-web \
                         -Dversion=xxx \
                         -Dpackaging=jar \
                         -DpomFile=./pom.xml
```

## 快速开始
> 注意：由于业务线程使用的虚拟线程，需要依赖 JDK 21+

1.引入相关的依赖
```xml
<dependencies>
    <dependency>
        <groupId>org.turbo</groupId>
        <artifactId>turbo-web</artifactId>
        <version>2025.03-alpha</version>
    </dependency>
</dependencies>
```
2.创建一个controller接口
```java
@RequestPath("/hello")
public class HelloController {
	@Get
	public String hello(HttpContext c) {
		return "hello world";
	}
}
```
3.编写服务器启动类
```java
public class Application {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(Application.class, 1);
		server.controllers(new HelloController());
		server.start();
	}
}
```
启动服务器之后浏览器访问：http://localhost:8080/hello 看到Hello World表示运行成功。
### 入门代码解析
- RequestPath：该注解用于标识控制器，参数表示控制器的路径，即请求的URL路径。
- Get：该注解用于标识控制器的方法，表示该方法支持GET请求，即该方法可以处理HTTP GET请求。
- HttpContext：该参数表示请求的上下文，包含了请求的参数、响应、请求体等信息，开发者可以利用该参数进行业务逻辑处理。
- TurboWebServer：该类是框架的核心类，用于启动服务器，并管理请求处理逻辑，参数是主启动类的字节码对象和IO线程数量（监听网络IO事件的线程，一半配置少量即可，上述代码配置一个）。
- StandardTurboWebServer：该类是TurboServer的默认实现，提供了一些常用的功能，如添加控制器、启动服务器等。
- controllers：该方法用于添加控制器，参数表示控制器对象，框架会自动扫描控制器的方法，并根据方法的注解进行路由映射。
- start：该方法用于启动服务器，参数表示监听端口号，启动成功后，服务器将开始监听请求，并根据路由映射规则处理请求。

## 路由的映射
在 Turbo-web 中，路由的映射是通过注解来实现的。在控制器的方法上添加注解，并指定请求的路径，即可实现路由的映射。

@RequesePath表示当前控制器对应的请求路径, 参数是路径名/开始,该路径与方法路径拼接之后是完整的路径。
@Get 表示当前方法支持GET请求，参数是请求路径，需要/开头。
```java
@Get
public String doGet(HttpContext c) {
    return "doGet";
}
```
@Post 表示当前方法支持POST请求，参数是请求路径，需要/开头。
```java
@Post
public String doPost(HttpContext c) {
    return "doPost";
}
```
@Put 表示当前方法支持PUT请求，参数是请求路径，需要/开头。
```java
@Put
public String doPut(HttpContext c) {
    return "doPut";
}
```
@Patch 表示当前方法支持PATCH请求，参数是请求路径，需要以/开头。

```java
@Patch
public String doPatch(HttpContext c) {
    return "doPatch";
}
```

@Delete 表示当前方法支持DELETE请求，参数是请求路径，需要/开头。

```java
@Delete
public String doDelete(HttpContext c) {
    return "doDelete";
}
```
> 注意：Get和Delete不携带请求体。

## 数据的响应

TurboWeb提供了两种响应的方式：

- 通过HttpContext的API。
- 直接将内容return出去（推荐）。

TurboWeb这两种方式都可以响应数据，但是在每一个路由中只能使用其中一种方式。如果没有特殊的需求，建议通过return的方式返回数据，因为HttpContext和return如果同时使用的话**HttpContext的优先级要比return高**，TurboWeb一旦检测到HttpContext中被写入内容，那么return的内容将不会被处理。

### 通过HttpContext响应数据

> HttpContext提供了一系列简化的常用API，在某些特殊场景的时候用起来会比直接return方便，例如要手动控制响应的格式、设置响应的状态码等。

1.响应text格式内容

```java
@Get
public void hello(HttpContext ctx) {
    ctx.text("Hello Turbo Web!");
}
```

2.响应html格式内容

```java
@Get
public void hello(HttpContext ctx) {
    ctx.html("<h1>Hello Turbo Web!</h1>");
}
```

3.响应json格式内容

```java
@Get
public void hello(HttpContext ctx) {
    User user = new User();
    user.setName("Turbo web");
    user.setAge(18);
    ctx.json(user);
}
```

Turbo-web也提供了响应时指定响应状态码的便捷操作

```java
@Get
public void hello(HttpContext ctx) {
    ctx.text(HttpResponseStatus.OK, "Hello World");
}
```

html和json格式的也是类似。

### 获取响应对象进行精细化的控制

```java
@Get
public void hello(HttpContext ctx) {
    HttpInfoResponse response = ctx.getResponse();
    ctx.text("Hello Turbo Web!");
    response.setStatus(HttpResponseStatus.OK);
}
```

> 注意：不推荐在response中写入内容，这会造成内容重复写入，推荐使用HttpContext的方法写入内容，如果要设置状态码需要在调用写入方法知乎，因为写入时不指定状态码会默认设置200。

### 直接通过返回值的方式

通过return返回内容的方式有一个需要注意的地方：

- 如果return的结果类型如果是字符串，那么会被作为text\plain处理。
- 如果return的结果是HttpResponse类型，并且HttpContext没有写入内容，那么TurboWeb会直接将HttpResponse返回给客户端（主要用于实现精细化控制）。
- 如果不是以上两种，TurboWeb会自动对返回结果进行序列化，并被作为application/json格式处理。8

```java
@Get
public User hello(HttpContext ctx) {
    User user = new User();
    user.setName("Turbo web");
    user.setAge(18);
    return user;
}
```

### 响应自定义的response对象

```java
@Get
public HttpResponse index(HttpContext ctx) {
    HttpInfoResponse response = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    response.setContent("Hello World!");
    response.setContentType("text/plain;charset=UTF-8");
    return response;
}
```

> 这里的response对象也可以直接使用HttpContext中的response对象，调度器会自动检测是否使用的是Context来保证资源的释放。

## 获取请求数据
> 注意：旧的API依然可以使用，但是新的API更加精简，建议使用新的API。
### 路径参数
在 Turbo-web 中，路径参数是通过在路径中添加占位符的方式来实现的。占位符的格式为 `{name}`，其中 `name` 是一个标识符，用于表示参数的名称。
```java
@Get("/{name}")
public String hello(HttpContext c) {
    String name = c.param("name");
    return "hello " + name;
}
```
获取路径参数通过ctx.param("name")获取，其中参数是占位符的名称。

TurboWeb也提供了一系列自动类型转换的方法，如下：

封装为Long类型：

```java
Long id = c.paramLong("id");
```

封装为Integer类型：

```java
Integer age = c.paramInt("age");
```

封装为Boolean类型：

```java
Boolean sex = c.paramBoolean("sex");
```

### 查询参数
TurboWeb除了路径参数之外的参数如果要进行封装，需要借助实体类封装，无法单独获取某个参数。

1.创建一共实体类，提供无参构造方法和get，setter方法。
```java
public class User {
    
    private String name;
    private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{" +
            "name='" + name + '\'' +
            ", age=" + age +
            '}';
    }
}
```
2.使用ctx.loadQuery方法封装
```java
@Get
public User user(HttpContext c) {
    User user = c.loadQuery(User.class);
    System.out.println(user);
    return user;
}
```
这个方法会自动将查询参数封装为对象，如果不携带查询参数，那么就封装null。

### 请求体的form表单
在 Turbo-web 中，请求体的form表单是通过HttpContext的loadForm方法来实现的。该方法会自动将请求体的form表单封装为对象，如果不携带form表单，那么就封装null。
```java
@Post
public User saveUser(HttpContext c) {
    User user = c.loadForm(User.class);
    System.out.println(user);
    return user;
}
```
### 封装json参数
在 Turbo-web 中，封装json参数是通过HttpContext的loadJson方法来实现的。该方法会自动将请求体的json参数封装为对象，缺少的json参数，会封装为null。
```java
@Post
public User saveUser(HttpContext c) {
    User user = c.loadJson(User.class);
    System.out.println(user);
    return user;
}
```
> 注意：如果请求体是空字符串，那么这个方法会抛出一个TurboParamParseException异常。

### 参数的校验
在 Turbo-web 中，参数的校验是通过注解来实现的。在参数上添加注解，并指定校验规则，即可实现参数的校验。

这里以User类为例子：
```java
public class User {

	@NotBlank(message = "name is required")
	private String name;
	@Max(value = 100, message = "age must be less than 100")
	@Min(value = 0, message = "age must be greater than 0")
	private int age;

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public int getAge() {
		return age;
	}

	@Override
	public String toString() {
		return "User{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
```
然后获取参数之后通过HttpContext的validate方法校验：
```java
@Post
public User saveUser(HttpContext c) {
    User user = c.loadJson(User.class);
    c.validate(user);
    System.out.println(user);
    return user;
}
```
数据校验失败就会抛出TurboArgsValidationException，里面封装了校验异常的message。

查询参数和表单参数也是类似的操作。

Turbo-web开提供了一种封装参数之后自动校验的机制，就是携带loadVaild前缀的方法：
```java
@Post
public User saveUser(HttpContext c) {
    User user = c.loadValidJson(User.class);
    System.out.println(user);
    return user;
}
```
这个方法就是将封装参数和validate方法合并起来，查询参数和表单参数也有类似的方法。

## 文件的操作

### 文件的上传

获取单个文件：

```java
@Post
public String upload(HttpContext c) {
    FileUpload fileUpload = c.loadFile("file");
    System.out.println(fileUpload);
    return "ok";
}
```

如果上传的文件是多个：

```java
@Post
public String upload(HttpContext c) {
    List<FileUpload> fileUploads = c.loadFiles("file");
    for (FileUpload fileUpload : fileUploads) {
        System.out.println(fileUpload.getFilename());
    }
    return "ok";
}
```

TurboWeb上传文件之后会在磁盘上创建文件进行落盘处理，这在上传大文件时可以有效的防止内存膨胀，通过打印FileUpload对象就可以查看文件临时存储的位置，而且在当前请求结束之后，TurboWeb会自动删除这个临时落盘的文件。

下面来看一下如何对上传文件进行处理。

获取文件的字节数组：

```java
byte[] bytes = fileUpload.get();
```

这个操作会直接将文件的所有字节读取到Java的堆内存，在文件较大的时候不推荐使用，容易出现OOM。

获取落盘之后的文件对象：

```java
File file = fileUpload.getFile();
```

将文件保存到指定的位置：

```java
fileUpload.renameTo(new File("D:\\test.txt"));
```

这个操作不会将文件的数据读取到内存，而是直接利用操作系统的文件操作来实现。

### 文件的下载

TurboWe对文件的下载提供了三种方式：

- 通过HttpContext的download方法进行下载。
- 通过FileStreamResponse进行文件下载。
- 通过FileRegionResponse进行文件下载（零拷贝）。

> 注意：
>
> HttpContext的download会将文件全部读取到内存再下载，性能比较低下，仅仅适用于小文件或者内存中的文件。
>
> FileStreamResponse采用的是便读取边下载的方式，文件的数据通过直接缓冲区缓存，不会到达Java的堆内存，非常适合大文件，但是会阻塞当前处理请求的线程，因此通常建议在VirtualHttpScheduler中使用。
>
> FileRegionResponse是采用的零拷贝进行文件的下载，非常高效，但是HTTPS中可能不适用。
>
> - 反应式调度器中不推荐使用前两种文件下载的方式，推荐直接零拷贝。
> - 如果针对大文件，优先选择使用FileRegionResponse，仅仅在零拷贝不可以时再考虑FileStreamResponse。

####  使用HttpContext的download进行文件下载

```java
@Get("/download")
public void download(HttpContext c) {
    String path = "C:\\Users\\heimi\\Downloads\\img.png";
    c.download(new File(path));
}
```

这种方式进行文件下载的时候会将文件内容全部读取到jvm内存之中然后进行下载，因此不适合大文件的下载。

HttpContext的download方法还提供了几个重载：

```java
@SyncOnce
Void download(HttpResponseStatus var1, byte[] var2, String var3);
```

- var1 响应状态码。
- var2 文件的字节数组。
- var3 文件名。

```java
@SyncOnce
Void download(byte[] var1, String var2);
```

- var1 文件的字节数组。
- var2 文件名。

```java
@SyncOnce
Void download(HttpResponseStatus var1, File var2);
```

- var1 响应状态码。
- var2 文件对象。

```java
@SyncOnce
Void download(File var1);
```

- var1 文件对象。

```java
@SyncOnce
Void download(HttpResponseStatus var1, InputStream var2, String var3);
```

- var1 响应状态码。
- var2 文件的输入流。
- var3 文件名。

```java
@SyncOnce
Void download(InputStream var1, String var2);
```

- var1 文件的输入流。
- var2 文件名。



## 中间件

### 中间件的基本使用

Middleware 类是所有中间件的基类。它定义了中间件的基本结构和行为，允许开发者通过继承该类并实现 invoke 方法来创建自定义中间件。每个中间件都持有一个指向下一个中间件的引用（通过 next 字段），这使得多个中间件可以按顺序执行。

中间件的执行顺序是根据添加顺序来确定的。

1.定义中间件
```java
public class SimpleMiddleware extends Middleware {
    @Override
    public Object invoke(HttpContext httpContext) {
        System.out.println("before...");
        Object object = httpContext.doNext();
        System.out.println("after...");
        return object;
    }
}
```
2.注册中间件
```java
public class Application {
    public static void main(String[] args) {
        TurboServer turboServer = new DefaultTurboServer(8);
        turboServer.addController(new HelloController());
        turboServer.addMiddleware(new SimpleMiddleware());
        turboServer.start(8080);
    }
}
```
中间件也可以被看作一个处理器，中间件自身也可以像controller一样处理请求，如果不调用doNext就不会执行后续的操作。
>注意：HttpContext中带有@End注解的方法都属于终止操作符，在本次请求中都会直接中断后续的中间件的执行。

TurboWeb的大多数功能都是基于中间件的扩展，下面介绍一下TurboWeb默认提供的一系列中间件。

### 静态资源的支持

#### 使用步骤

1.注册静态资源中间件

```java
TurboServer server = new DefaultTurboServer(Application.class, 8);
server.addMiddleware(new StaticResourceMiddleware());
```

2.将静态资源放入static目录之下即可访问

#### 配置静态资源

```java
StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();
// 请求以/static开头会被拦截作为静态资源处理
staticResourceMiddleware.setStaticResourceUri("/static");
// 静态资源在resource中的位置
staticResourceMiddleware.setStaticResourcePath("static");
// 是否对静态资源进行缓存
staticResourceMiddleware.setCacheStaticResource(true);
// 缓存多大内存以内的静态资源
staticResourceMiddleware.setCacheFileSize(1024 * 1024 * 10);
```

### 模板的使用

> TurboWeb默认提供了Freemarker模板

1.注册中间件

```java
server.addMiddleware(new FreemarkerTemplateMiddleware());
```

2.在templates文件夹下创建模板

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
</head>
<body>
<#list names as name>
    ${name}
    <br/>
</#list>
</body>
</html>
```

3.在控制器中渲染模板

```java
@Get
public ViewModel index(HttpContext ctx) {
    ViewModel viewModel = new ViewModel();
    List<String> names = List.of("张三", "李四", "王五");
    viewModel.addAttribute("names", names);
    viewModel.setViewName("index");
    return viewModel;
}
```

> 注意：注册模板中间件之后只要返回值类型是ViewModel会自动被拦截进行渲染，其他类型的不受影响。

#### 模板渲染中间件的配置

```java
FreemarkerTemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();
// 设置模板的路径
templateMiddleware.setTemplatePath("templates");
// 设置模板的后缀
templateMiddleware.setTemplateSuffix(".ftl");
// 是否开启模板缓存
templateMiddleware.setOpenCache(true);
```

### 两级限流策略

对于大规模的请求，虽然TurboWeb可以高效的处理，但是有一个非常验证的问题就是每个请求的处理逻辑可能都需要占用一部分的内存，随着并发请求的增加，很容易会造成OOM，因此在一些场景需要限制同时处理的请求数。

由于TurboWeb和传统的web框架不同，没有采用固定的线程池，而是基于虚拟线程/Reactor反应式调度，因此无法通过限制线程池的方式来控制同时处理的请求数。

TurboWeb提供了两级限流策略来限制同时处理的请求数。

#### 一级限流策略

一级限流策略也被称为全局限流策略，是针对于整个服务器实例来说的可以同时处理的请求数。

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    AbstractGlobalConcurrentLimitMiddleware globalLimit = new AbstractGlobalConcurrentLimitMiddleware(10) {
        @Override
        public Object doAfterReject(HttpContext ctx) {
            return ctx.text("too many requests");
        }
    };
    server.addMiddleware(globalLimit);
    server.start();
}
```

- 这段代码标识在这个服务器实例中，最多同时处理10个请求。
- doAfterReject这个回调是到达流量限制之后触发，推荐在这个回调中直接返回响应不要进行业务处理。

#### 二级限流策略

二级限流策略是更加细粒度的限流，可以根据不同的请求方式和url前缀配置不同的限流规则。

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    AbstractConcurrentLimitMiddleware concurrentLimitMiddleware = new AbstractConcurrentLimitMiddleware() {
        @Override
        public Object doAfterReject(HttpContext ctx) {
            return ctx.text("too many requests");
        }
    };
    concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/hello", 32);
    concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/hello2", 64);
    concurrentLimitMiddleware.addStrategy(HttpMethod.GET, "/hello3", 128);
    server.addMiddleware(concurrentLimitMiddleware);
    server.start();
}
```

- 这里的回调也是当请求被拒绝之后会触发。
- 二级限流可以针对不同的请求方式和url前缀配置不同的策略。

> 注意：
>
> 一级限流策略和二级限流策略可以同时使用。
>
> 推荐：一级限流策略的中间件位于二级限流策略之前。

### 信息采集中间件

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    server.addMiddleware(new ServerInfoMiddleware());
    server.start();
}
```

- GET方式请求，默认路径为/turboWeb/serverInfo，可以修改。
- /turboWeb/serverInfo?type=memory：查看内存的使用情况。
- /turboWeb/serverInfo?type=thread：查看线程的信息。
- /turboWeb/serverInfo?type=gc：查看垃圾回收器的信息。

### CORS中间件

`CorsMiddleware` 是 TurboWeb 提供的用于处理跨域请求（CORS: Cross-Origin Resource Sharing）的中间件，实现了浏览器中前端与后端跨域通信的必要支持。

该中间件默认允许所有源的跨域访问，并支持自定义配置，用于在生产环境中精细控制跨域策略。

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    CorsMiddleware corsMiddleware = new CorsMiddleware();
    server.addMiddleware(corsMiddleware);
    server.start();
}
```

默认的配置：

- 允许所有域名访问（`*`）
- 支持方法：`GET`、`POST`、`PUT`、`DELETE`
- 允许所有请求头（`*`）
- 暴露响应头：`Content-Disposition`
- 不允许携带 Cookie（`Allow-Credentials = false`）
- 预检请求结果缓存时间：3600 秒

自定义配置：

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    CorsMiddleware cors = new CorsMiddleware();
    // 指定允许的跨域来源
    cors.setAllowedOrigins(List.of("https://example.com"));
    // 指定允许的 HTTP 方法
    cors.setAllowedMethods(List.of("GET", "POST"));
    // 指定允许的请求头
    cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    // 指定哪些响应头可以暴露给客户端
    cors.setExposedHeaders(List.of("Content-Disposition"));
    // 是否允许携带 Cookie
    cors.setAllowCredentials(true);
    // 设置预检请求的缓存时间（单位：秒）
    cors.setMaxAge(1800);
    server.addMiddleware(cors);
    server.start();
}
```

## 异常处理器
异常处理器可以用于优雅的处理业务代码中出现的异常。
1.定义异常处理器
```java
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public String handleRuntimeException(RuntimeException e) {
        return "500";
    }
}
```
2.注册异常处理器
```java
public class Application {
    public static void main(String[] args) {
        TurboServer turboServer = new DefaultTurboServer(8);
        turboServer.addController(new HelloController());
        turboServer.addExceptionHandler(new GlobalExceptionHandler());
        turboServer.start(8080);
    }
}
```
异常处理器中可以指定响应的状态码。
```java
@ExceptionHandler(RuntimeException.class)
@ExceptionResponseStatus(500)
public String handleRuntimeException(RuntimeException e) {
    return "500";
}
```
> 注意：异常处理器中参数是注解中异常类或者父类，异常处理器可以通过return确定要返回的结果，会直接当作json处理。

## Cookie的使用
Cookie的获取通过HttpContext的request对象来获取:
```java
@Get
public void hello(HttpContext ctx) {
    HttpInfoRequest request = ctx.getRequest();
    Cookies cookies = request.getCookies();
    String name = cookies.getCookie("name");
    ctx.text("hello " + name);
}
```
如果要设置Cookie，需要通过HttpContext的response对象来设置:
```java
@Get
public void hello(HttpContext ctx) {
    ctx.getResponse().setCookie("name", "turbo");
    ctx.text("success");
}
```
Cookie也支持直接通过HttpContext获取HttpCookie进行操作。
```java
@Get
public void index(HttpContext ctx) {
    HttpCookie httpCookie = ctx.getHttpCookie();
    httpCookie.setCookie("name", "turbo");
    httpCookie.setCookie("version", "1.2.0");
    ctx.text("hello world");
}
```
HttpCookie不仅可以存储Cookie，也可以获取Cookie
```java
@Get
public void index(HttpContext ctx) {
    HttpCookie httpCookie = ctx.getHttpCookie();
    String name = httpCookie.getCookie("name");
    ctx.text(name);
}
```

## Session的使用
Session的获取通过HttpContext的request对象来获取。
设置Session的内容:
```java
@Get
public void hello(HttpContext ctx) {
    HttpInfoRequest request = ctx.getRequest();
    Session session = request.getSession();
    session.setAttribute("name", "turbo");
    ctx.text("success");
}
```
设置Session的时可以指定过期事件，单位是毫秒:
```java
@Get
public void hello(HttpContext ctx) {
    HttpInfoRequest request = ctx.getRequest();
    Session session = request.getSession();
    session.setAttribute("name", "turbo", 1000 * 10);
    ctx.text("success");
}
```
获取Session中的数据:
```java
@Get
public void hello(HttpContext ctx) {
    HttpInfoRequest request = ctx.getRequest();
    Session session = request.getSession();
    String name = (String) session.getAttribute("name");
    ctx.text("hello " + name);
}
```
session中获取的结果会以Object类型返回，需要手动转换类型。


HttpContext也提供了对Session的简化操作。
```java
@Get
public void index(HttpContext ctx) {
    Session session = ctx.getSession();
    session.setAttribute("name", "turbo");
    ctx.json("success");
}
```
也支持从中获取session
```java
@Get
public void index(HttpContext ctx) {
    Session session = ctx.getSession();
    String name = (String) session.getAttribute("name");
    ctx.json(name);
}
```
## SSE的使用
> SSE（Server-Sent Events，服务器推送事件）是一种基于 HTTP 的单向数据推送技术，允许 服务器主动向客户端发送数据，而客户端使用 EventSource API 监听和处理这些数据。

SSE在TurboWeb中使用比较简单，在HttpContext中操作即可
```java
@Get
public HttpResponse index(HttpContext ctx) {
    // 开启SSE会话
    SseResultObject sseResultObject = ctx.openSseSession();
    // 获取SSE会话
    SSESession session = sseResultObject.getSseSession();
    // 不断发送消息
    Thread thread = Thread.ofVirtual().start(() -> {
        while (true) {
            session.send("hello world");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("thread interrupt");
                return;
            }
        }
    });
    // 监听session的销毁事件
    session.closeListener(() ->{
        thread.interrupt();
        System.out.println("session destroy");
    });
    // 通知浏览器，SSE会话已经建立
    return sseResultObject.getHttpResponse();
}
```
> 注意：若想使用SSE，返回值必须使用SseResultObject中的HttpResponse进行返回，因为该对象设置了相关的响应内容。

## WebSocket的使用

1.创建websocket处理器实现WebSocketHandler

```java
public class MyWebSocketHandler implements WebSocketHandler {

    @Override
    public void onOpen(WebSocketSession session) {
        System.out.println("onOpen");
    }

    @Override
    public void onMessage(WebSocketSession session, WebSocketFrame webSocketFrame) {
        if (webSocketFrame instanceof TextWebSocketFrame textWebSocketFrame) {
            System.out.println(textWebSocketFrame.text());
            textWebSocketFrame.release();
        } else if (webSocketFrame instanceof BinaryWebSocketFrame binaryWebSocketFrame) {
            System.out.println("二进制帧");
            binaryWebSocketFrame.release();
        } else if (webSocketFrame instanceof PingWebSocketFrame) {
            System.out.println("ping");
        } else if (webSocketFrame instanceof PongWebSocketFrame) {
            System.out.println("pong");
        }
    }

    @Override
    public void onClose(WebSocketSession session) {
        System.out.println("onClose");
    }
}
```

- 当客户端向服务端推送不同的消息时都会触发onMessage方法的回调。
- TextWebSocketFrame：客户端发送的文本帧。
- BinaryWebSocketFrame：客户端发送的二进制帧。
- PingWebSocketFrame：客户端发送的Ping帧。
- PongWebSocketFrame：客户端发送的Pong帧。

2.在服务器实例中添加自定义的websocket处理器

```java
public static void main(String[] args) {
    TurboServer server = new DefaultTurboServer(Application.class);
    server.setWebSocketHandler("/ws/(.*)", new MyWebSocketHandler());
    server.start();
}
```

- 第一个参数是websocket处理器需要处理的路径，可以使用正则表达式。
- 第二个参数是自定义的websocket处理器。

上面的处理器实现起来显得有些复杂，开发者不仅仅需要实现onOpen方法和onClose方法，还需要手动区分不同的帧进行不同的业务逻辑处理，而且还需要开发者手动释放资源，由于这里使用的直接内存，若开发者忘记释放会导致内存泄漏。

TurboWeb提供了一个更加简单的抽象类，AbstractWebSocketHandler，这个抽象类会进行不同帧的分发和资源的释放。

```java
public class MyWebSocketHandler extends AbstractWebSocketHandler {

    @Override
    public void onText(WebSocketSession session, String content) {
        System.out.println(content);
    }

    @Override
    public void onBinary(WebSocketSession session, ByteBuf content) {
        System.out.println("二进制帧");
    }
}
```

这个抽象类会自动根据不同的帧触发不同的回调，onText、onBinary、onPing、onPong等回调，需要开发者手动实现的只有onText和onBinary，分别对应文本帧和二进制帧，同时这个抽象会对onOpen和onClose有默认的实现。

## 服务器参数配置
Turbo-web提供了配置类，可以通过重新设置配置类的方式进行配置设置，如果不设置采用默认的参数。
```java
public class Application {
    public static void main(String[] args) {
        TurboServer turboServer = new DefaultTurboServer(8);
        ServerParamConfig config = new ServerParamConfig();
        config.setSessionCheckTime(1000 * 60 * 60);
        turboServer.setConfig(config);
        turboServer.start(8080);
    }
}
```

## Http客户端
> TurboWeb提供了两种客户端，一种是可同步阻塞的客户端，另一种是反应式客户端，无论哪种客户端，底层都是基于反应式客户端实现的。
### PromiseHttpClient
> 看名字这个客户端是返回一个Promise对象，这一个是支持同步阻塞的客户端，推荐在Loom调度器中使用。

#### 发送Http请求
> 这里以Get请求为例，更加详细的操作可以查看reactor netty的httpClient,因为这里就是对HttpClient的封装。

```java
public static void main(String[] args) throws InterruptedException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    test();
}

public static void test() throws ExecutionException, InterruptedException {
    // 通过http客户端工具获取客户端
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    FullHttpResponse fullHttpResponse = promiseHttpClient.request((httpClient) -> httpClient.request(HttpMethod.GET)
            .uri("http://localhost:8080/user"))
        .get();
    System.out.println(fullHttpResponse.status());
}
```
> 注意，
>
>http客户端在使用的时候需要初始化，如果是在TurboWeb运行过程中使用那么不需要初始化，因为TurboWeb在启动的过程中会自动完成初始化。

#### 使用简化的API
> 上面的操作返回值是FullHttpResponse对象，这样的操作比较麻烦，TurboWeb提供了简化的API，可以简化为下面的操作。

1.发送Get请求
```java
public static void main(String[] args) throws InterruptedException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送Get请求并且等待结果的获取
    RestResponseResult<Map> responseResult = promiseHttpClient.get("http://localhost:8080/hello", Map.of("name", "turbo")).get();
    // 获取相应头
    System.out.println(responseResult.getHeaders());
    // 获取响应体
    System.out.println(responseResult.getBody());
}
```
Get请求提供了三种重载的API
```java
// url 请求地址
// headers 请求头
// params 查询参数，如果没有传入空集合或null即可
// type 返回值类型
public <T> Promise<RestResponseResult<T>> get(String url, HttpHeaders headers, Map<String, String> params, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
// type 返回值类型
public <T> Promise<RestResponseResult<T>> get(String url, Map<String, String> params, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
public <T> Promise<RestResponseResult<Map>> get(String url, Map<String, String> params)
```
2.Post请求

Post请求可以发送表单数据也可以发送json数据

发送表单数据：
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送post请求
    RestResponseResult<Map> responseResult = promiseHttpClient.postForm("http://localhost:8080/hello", Map.of(), Map.of()).get();
    System.out.println(responseResult.getHeaders());
    System.out.println(responseResult.getBody());
}
```
方法介绍
```java
// url 请求地址
// headers 请求头
// params 查询参数，如果没有传入空集合或null即可
// forms 表单数据
// type 返回值类型
public <T> Promise<RestResponseResult<T>> postForm(String url, HttpHeaders headers, Map<String, String> params, Map<String, String> forms, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
// forms 表单数据
// type 返回值类型
public <T> Promise<RestResponseResult<T>> postForm(String url, Map<String, String> params, Map<String, String> forms, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
// forms 表单数据
public Promise<RestResponseResult<Map>> postForm(String url, Map<String, String> params, Map<String, String> forms)
```
发送json数据：
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送post请求
    RestResponseResult<Map> responseResult = promiseHttpClient.postJson("http://localhost:8080/hello", Map.of(), new UserDTO()).get();
    System.out.println(responseResult.getHeaders());
    System.out.println(responseResult.getBody());
}
```
方法介绍
```java
// url 请求地址
// headers 请求头
// params 查询参数，如果没有传入空集合或null即可
// bodyContent 请求体数据，传入对象会自动序列化为json
// type 返回值类型
public <T> Promise<RestResponseResult<T>> postJson(String url, HttpHeaders headers, Map<String, String> params, Object bodyContent, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
// bodyContent 请求体数据，传入对象会自动序列化为json
// type 返回值类型
public <T> Promise<RestResponseResult<T>> postJson(String url, Map<String, String> params, Object bodyContent, Class<T> type)
```
```java
// url 请求地址
// params 查询参数，如果没有传入空集合或null即可
// bodyContent 请求体数据，传入对象会自动序列化为json
public Promise<RestResponseResult<Map>> postJson(String url, Map<String, String> params, Object bodyContent)
```
3.Put请求

发送表单数据：
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送put请求
    RestResponseResult<Map> responseResult = promiseHttpClient.putForm("http://localhost:8080/hello", Map.of(), Map.of()).get();
    System.out.println(responseResult.getHeaders());
    System.out.println(responseResult.getBody());
}
```
发送Json数据：
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送put请求
    RestResponseResult<Map> responseResult = promiseHttpClient.putJson("http://localhost:8080/hello", Map.of(), new UserDTO()).get();
    System.out.println(responseResult.getHeaders());
    System.out.println(responseResult.getBody());
}
```
由于Put和Post的重载方法一致，这里就不过多介绍了。

4.Delete请求
```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    // 发送delete请求
    RestResponseResult<Map> responseResult = promiseHttpClient.delete("http://localhost:8080/hello", Map.of()).get();
    System.out.println(responseResult.getHeaders());
    System.out.println(responseResult.getBody());
}
```
Delete的重载方法和Get的类似。

异步处理结果，上面的实例中都是通过同步的方式处理的结果，下面展示异步获取结果

```java
public static void main(String[] args) throws InterruptedException, ExecutionException {
    HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
    PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
    promiseHttpClient.get("http://localhost:8080/hello", new HashMap<>())
        .addListener(future -> {
            if (future.isSuccess()) {
                RestResponseResult<String> responseResult = (RestResponseResult<String>) future.get();
                System.out.println(responseResult.getHeaders());
                System.out.println(responseResult.getBody());
            } else {
                future.cause().printStackTrace();
            }
        });
}
```
> 注意：
> 
> 禁止在addListener中调用阻塞的方法，否则会阻塞事件循环线程。
> 
> 一般情况下使用同步获取结果即可，因为这个客户端是专为Loom调度器设计的，让Loom线程处于短暂的等待是可以接收的。

### ReactiveHttpClient
> ReactiveHttpClient是专为Reactive调度器设计的，使用方式类似PromiseHttpClient，但是返回值是一个Mono对象，可以用于构建Reactive Stream，这里就不过多介绍了。

## 生命周期相关
> TurboWeb提供了两种生命周期相关的方法，分别是：服务器启动过程的生命周期，和http调度器的生命周期。

### 服务器启动相关的生命周期
1.创建初始化类
```java
public class ServerInitConfig implements TurboServerInit {
    @Override
    public void beforeTurboServerInit(ServerBootstrap serverBootstrap) {
        System.out.println("""
            这个是对serverBootStrap初始化之前调用，
            这时的serverBootStrap是刚创建的对象
            """);
    }

    @Override
    public void afterTurboServerInit(ServerBootstrap serverBootstrap) {
        System.out.println("""
            这是在serverBootStrap调用初始化方法之后调用，
            这时eventLoop和系统内置的handler被设置完成。
            """);
    }

    @Override
    public void afterTurboServerStart() {
        System.out.println("""
            这是在服务器启动之后调用的方法
            """);
    }
}
```
2.在server中添加初始化对象
```java
server.addTurboServerInit(new ServerInitConfig());
```
> 初始化器可以添加多个，多个初始化器按照顺序执行，先调用所有的beforeTurboServerInit，之后初始化完成是调用所有的afterTurboServerInit，等服务器启动之后调用所有的afterTurboServerStart。

### 中间件相关的生命周期
> 中间件的初始化分为三步：
> 
> 1.创建中间件的执行链，中间件的执行顺序被确定。
> 
> 2.依赖注入阶段，调度器会判断中间件实现了那些Aware接口，并且为其注入框架内部资源。
> 
> 3.init方法执行阶段，这时所有依赖被注入完毕，开始调用所有中间件的init方法，在init方法中可以拿到中间件的执行链，可以对其结构进行修改(但是不推荐)。

定义中间件，实现Aware接口
```java
public class MyMiddleware extends Middleware implements CharsetAware, ExceptionHandlerMatcherAware, MainClassAware, SessionManagerProxyAware {

    private Charset charset;
    private ExceptionHandlerMatcher exceptionHandlerMatcher;
    private Class<?> mainClass;
    private SessionManagerProxy sessionManagerProxy;


    @Override
    public Object invoke(HttpContext ctx) {
        return ctx.doNext();
    }

    @Override
    public void init(Middleware chain) {
        System.out.println("MyMiddleware init");
    }

    @Override
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void setExceptionHandlerMatcher(ExceptionHandlerMatcher matcher) {
        this.exceptionHandlerMatcher = matcher;
    }

    @Override
    public void setMainClass(Class<?> mainClass) {
        this.mainClass = mainClass;
    }

    @Override
    public void setSessionManagerProxy(SessionManagerProxy sessionManagerProxy) {
        this.sessionManagerProxy = sessionManagerProxy;
    }
}
```
## 节点共享
> TurboWeb 的节点共享主要是指在多个 TurboWeb 实例之间共享服务能力，让微服务之间的调用更加高效，无需额外的代理或网关。

首先需要创建两个服务器实例

订单服务实例：
```java
@RequestPath("/order")
public class OrderController {
    @Get
    public void helloOrder(HttpContext ctx) throws InterruptedException {
        ctx.text("hello order");
    }
}
```
```java
public class OrderApplication {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(OrderApplication.class, 8);
        server.addController(new OrderController());
        Gateway gateway = new DefaultGateway();
        // 配置节点
        gateway.addServerNode("/user", "http://localhost:8080");
        server.setGateway(gateway);
        server.start(8081);
    }
}
```
用户服务实例：
```java
@RequestPath("/user")
public class UserController {
    @Get
    public void helloUser(HttpContext ctx) {
        ctx.text("hello user");
    }
}
```
```java
public class UserApplication {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(UserApplication.class, 8);
        server.addController(new UserController());
        Gateway gateway = new DefaultGateway();
        gateway.addServerNode("/order", "http://localhost:8081");
        server.setGateway(gateway);
        server.start(8080);
    }
}
```
之后对外部来说，这两个实例中任意一个实例都包含了两个实例中所有的功能，就好比两个实例都运行一样的代码。

例如：访问http://localhost:8080/user，那么会调用自身的处理器处理，如果访问http://localhost:8080/order，由于8080服务器上配置了/order节点，
那么8080这个实例会在内置的网关中直接将请求代理到8081节点，因此对外界调用者来说，任意一个节点都拥有完整集群中所有的功能。
## 反应式编程的支持

> TurboWeb中使用反应式编程比较简单，用户在server中切换使用反应式调度器即可，由于大多数的web操作都是请求响应模型，因此当异步对象到达http调度器时必须是Mono这种单流对象，否则会报错。

1.切换为反应式编程

```java
public class Application {
    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class, 8);
        server.addController(HelloController.class);
        // 切换为反应式编程
        server.setIsReactiveServer(true);
        server.start(8080);
    }
}
```

> 注意：不同同时使用反应式编程和同步代码。

2.在反应式中使用SSE

```java
@Get
public Mono<HttpResponse> index(HttpContext ctx) {
    // 开启SSE会话
    SseResultObject sseResultObject = ctx.openSseSession();
    // 获取SSE会话
    SSESession session = sseResultObject.getSseSession();
    // 不断发送消息
    Thread thread = Thread.ofVirtual().start(() -> {
        while (true) {
            session.send("hello world");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("thread interrupt");
                return;
            }
        }
    });
    // 监听session的销毁事件
    session.closeListener(() ->{
        thread.interrupt();
        System.out.println("session destroy");
    });
    // 通知浏览器，SSE会话已经建立
    return Mono.just(sseResultObject.getHttpResponse());
}
```

反应式的整体操作对于TurboWeb来说差异不大，只不过返回值需要返回用户所构造的反应式流。

> 注意：反应式中不能使用HttpContext进行返回结果，例如json,html,text等操作禁止使用，需要通过返回值返回。

Turbo原生的中间件也支持反应式，但是在反应式编程使用原生的中间件操作可能需要强制类型转换再进行操作。

```java
public class MyMiddleware extends Middleware {

    @Override
    public Object invoke(HttpContext ctx) {
        return ((Mono<?>) ctx.doNext())
            .map(r ->{
                System.out.println("执行之后的逻辑...");
                return r;
            });
    }

}
```

TurboWeb提供了ReactiveMiddleware继承Middleware，与原来的没有区别，只不过返回值是Mono<?>。

在反应式中推荐使用ctx.doNextMono()，这样可以避免手动强制类型转化，这个操作会自动将Mono<?>转换出，doNextMono()和doNext()的作用是一样的，因此同时使用都可以，都会调用下一个中间件，但是推荐使用一种。

```
public class MyMiddleware extends ReactiveMiddleware {

    @Override
    public Mono<?> doSubscribe(HttpContext ctx) {
        return ctx.doNextMono()
            .map(r -> {
                System.out.println("执行之后的逻辑...");
                return r;
            });
    }
}
```

> 注意：TurboWeb如果使用反应式编程，异常处理器的返回值也是需要Mono类型的。