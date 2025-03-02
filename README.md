# Turbo-web使用手册
> 版本 = 2025.03

## 升级内容
### 新增功能
> 1.增加StaticResourceMiddleware中间件，提供了对静态资源的支持。
> 
> 2.增加FreemarkerTemplateMiddleware中间件，提供了对freemarker模板的支持。
> 
> 3.提供一系列Aware接口，可以对Middleware进行底层对象依赖注入。
> 
> 4.增加了对SSE技术的支持。
> 
> 5.支持反应式编程，用户可以在服务启动时选择使用同步编程或反应式编程。
> 
> 6.提供了一系列系统生命周期的钩子操作。

### 原有功能的优化
> 1.简化用户对Cookie和Session操作的API。
> 
> 2.支持用户在返回值直接返回HttpResponse对象，框架会自动根据类型进行解析。
> 
> 3.对session进行了优化，优化了垃圾回收策略，抽象依赖，方便扩展。

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
        <version>1.2.0-alpha</version>
    </dependency>
</dependencies>
```
2.创建一个controller接口
```java
@RequestPath("/hello")
public class HelloController {
    
    @Get
    public void hello(HttpContext ctx) {
        ctx.text("Hello Turbo web!");
    }
}
```
3.编写服务器启动类
```java
public class Application {
    public static void main(String[] args) {
        TurboServer turboServer = new DefaultTurboServer(Application.class, 8);
        turboServer.addController(HelloController.class);
        turboServer.start(8080);
    }
}
```
启动服务器之后浏览器访问：http://localhost:8080/hello 看到Hello Turbo web!表示运行成功。
### 入门代码解析
- RequestPath：该注解用于标识控制器，参数表示控制器的路径，即请求的URL路径。
- Get：该注解用于标识控制器的方法，表示该方法支持GET请求，即该方法可以处理HTTP GET请求。
- HttpContext：该参数表示请求的上下文，包含了请求的参数、响应、请求体等信息，开发者可以利用该参数进行业务逻辑处理。
- TurboServer：该类是框架的核心类，用于启动服务器，并管理请求处理逻辑，参数表示worker线程数量，即用于监听read事件的线程数量。
- DefaultTurboServer：该类是TurboServer的默认实现，提供了一些常用的功能，如添加控制器、启动服务器等。
- addController：该方法用于添加控制器，参数表示控制器的字节码对象，框架会自动扫描控制器的方法，并根据方法的注解进行路由映射。
- start：该方法用于启动服务器，参数表示监听端口号，启动成功后，服务器将开始监听请求，并根据路由映射规则处理请求。

## 路由的映射
在 Turbo-web 中，路由的映射是通过注解来实现的。在控制器的方法上添加注解，并指定请求的路径，即可实现路由的映射。

@RequesePath表示当前控制器对应的请求路径, 参数是路径名/开始,该路径与方法路径拼接之后是完整的路径。
@Get 表示当前方法支持GET请求，参数是请求路径，需要/开头。
```java
@Get
public void doGet(HttpContext ctx) {
    ctx.text("doGet");
}
```
@Post 表示当前方法支持POST请求，参数是请求路径，需要/开头。
```java
@Post
public void doPost(HttpContext ctx) {
    ctx.text("doPost");
}
```
@Put 表示当前方法支持PUT请求，参数是请求路径，需要/开头。
```java
@Put
public void doPut(HttpContext ctx) {
    ctx.text("doPut");
}
```
@Delete 表示当前方法支持DELETE请求，参数是请求路径，需要/开头。
```java
@Delete
public void doDelete(HttpContext ctx) {
    ctx.text("doDelete");
}
```
> 注意：Get和Delete不携带请求体。

## 获取请求数据
### 路径参数
在 Turbo-web 中，路径参数是通过在路径中添加占位符的方式来实现的。占位符的格式为 `{name}`，其中 `name` 是一个标识符，用于表示参数的名称。
```java
@Get("/{name}")
public void hello(HttpContext ctx) {
    String name = ctx.getPathVariable("name");
    ctx.text("Hello " + name);
}
```
获取路径参数通过ctx.getPathVariable("name")获取，其中name是占位符的名称。
> 注意：路径参数默认的类型是String类型，如果是其它类型需要开发者手动转换。

### 查询参数
在 Turbo-web 中，查询参数是通过在路径中添加参数的方式来实现的。参数的格式为 `?name=value`，其中 `name` 是一个标识符，用于表示参数的名称，`value` 是参数的值。
```java
@Get
public void getUser(HttpContext ctx) {
    Map<String, List<String>> queryParams = ctx.getRequest().getQueryParams();
    List<String> name = queryParams.get("name");
    if (name != null && !name.isEmpty()) {
        ctx.text("Hello " + name.getFirst());
    } else {
        ctx.text("Hello World");
    }
}
```
在Turbo-web中查询参数会被封装成一共Map集合, 键为参数名，由于值可能多个，所以值为List集合。

虽然上面这种方式可以获取到查询参数，但是代码是太繁琐了，turbo-web提供了一种简介的参数封装方式。

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
2.使用ctx.loadQueryParamToBean方法封装
```java
@Get
public void getUser(HttpContext ctx) {
    User user = ctx.loadQueryParamToBean(User.class);
    ctx.json(user);
}
```
这个方法会自动将查询参数封装为对象，如果不携带查询参数，那么就封装null。

### 请求体的form表单
在 Turbo-web 中，请求体的form表单是通过ctx.loadFormParamToBean方法来实现的。该方法会自动将请求体的form表单封装为对象，如果不携带form表单，那么就封装null。
```java
@Post
public void saveUser(HttpContext ctx) {
    User user = ctx.loadFormParamToBean(User.class);
    ctx.json(user);
}
```
当然也可以通过request获取最原始的参数，类似查询参数一样。

### 封装json参数
在 Turbo-web 中，封装json参数是通过ctx.loadJsonParamToBean方法来实现的。该方法会自动将请求体的json参数封装为对象，缺少的json参数，会封装为null。
```java
@Post
public void saveUser(HttpContext ctx) {
    User user = ctx.loadJsonParamToBean(User.class);
    ctx.json(user);
}
```
> 注意：如果请求体是空字符串，那么这个方法会抛出一个TurboParamParseException异常。

### 参数的校验
在 Turbo-web 中，参数的校验是通过注解来实现的。在参数上添加注解，并指定校验规则，即可实现参数的校验。

这里以User类为例子：
```java
public class User {

    @NotBlank(message = "name不能为空")
    private String name;
    @NotNull(message = "age不能为空")
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
然后获取参数之后通过ctx.validate方法校验：
```java
@Post
public void saveUser(HttpContext ctx) {
    User user = ctx.loadJsonParamToBean(User.class);
    ctx.validate(user);
    ctx.json(user);
}
```
数据校验失败就会抛出TurboArgsValidationException，里面封装了校验异常的message。

查询参数和表单参数也是类似的操作。

Turbo-web开提供了一种封装参数之后自动校验的机制，就是携带loadVaild前缀的方法：
```java
@Post
public void saveUser(HttpContext ctx) {
    User user = ctx.loadValidJsonParamToBean(User.class);
    ctx.json(user);
}
```
这个方法就是将封装参数和validate方法合并起来，查询参数和表单参数也有类似的方法。

## 文件上传
```java
@Post
public void uploadFile(HttpContext ctx) throws IOException {
    List<FileUpload> file = ctx.getFileUploads("file");
    if (!file.isEmpty()) {
        FileUpload fileUpload = file.getFirst();
        fileUpload.renameTo(new File("D:\\" + fileUpload.getFilename()));
    }
    context.text("success");
}
```
文件上传在Turbo-web中，是通过ctx.getFileUploads方法来实现的。该方法会返回一个List集合，集合中的元素为FileUpload对象，FileUpload对象封装了文件上传的信息，包括文件名、文件大小、文件内容等。

也可以获取文件的字节信息：
```java
@Post
public void uploadFile(HttpContext context) throws IOException {
    List<FileUpload> file = context.getFileUploads("file");
    if (!file.isEmpty()) {
        FileUpload fileUpload = file.getFirst();
        byte[] bytes = fileUpload.get();
        System.out.printf("文件大小：%d%n", bytes.length);
    }
    context.text("success");
}
```
通过fileUpload的get方法即可获取文件的字节数组。

## 数据的响应
Turbo-web提供了两种数据响应的方式：一种是使用通过HttpContext的方法进行响应，响应内容的格式根据方法不同而不同；另一种直接return，如果return的是字符串，那么会被当作text格式的处理，否则被当成json格式进行处理。

如果两种方式同时使用，那么通过HttpContext调用方法的响应会优先生效。

### 通过HttpContext响应数据
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

### 直接通过返回值的方式
```java
@Get
public User hello(HttpContext ctx) {
    User user = new User();
    user.setName("Turbo web");
    user.setAge(18);
    return user;
}
```

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

## 中间件
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
        turboServer.addController(HelloController.class);
        turboServer.addMiddleware(new SimpleMiddleware());
        turboServer.start(8080);
    }
}
```
中间件也可以被看作一个处理器，中间件自身也可以像controller一样处理请求，如果不调用doNext就不会执行后续的操作。

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
        turboServer.addController(HelloController.class);
        turboServer.addExceptionHandler(GlobalExceptionHandler.class);
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
## 静态资源的支持
### 使用步骤
1.注册静态资源中间件
```java
TurboServer server = new DefaultTurboServer(Application.class, 8);
server.addMiddleware(new StaticResourceMiddleware());
```
2.将静态资源放入static目录之下即可访问
### 配置静态资源
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

## 模板的使用
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
### 模板渲染中间件的配置
```java
FreemarkerTemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();
// 设置模板的路径
templateMiddleware.setTemplatePath("templates");
// 设置模板的后缀
templateMiddleware.setTemplateSuffix(".ftl");
// 是否开启模板缓存
templateMiddleware.setOpenCache(true);
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