# 请求体系

控制器方法的形参有两种来源：

- 形参上标注取参注解，框架按注解从请求的对应位置取值，并转换为形参声明的类型；
- 形参直接声明框架对象（如 `HttpContext`），由框架注入。

取值的转换失败会抛异常，不会静默给出一个错误的值。没有解析器认领的形参绑定为 `null`。

## 变更说明

旧版把取参入口挂在 `HttpContext` 上：`query()`、`queries()`、`queryInt()`、`loadQuery()`、`loadForm()`、`loadJson()`、`loadFiles()`、`loadFile()` 等方法各自完成取数、类型转换与实体绑定。同一类数据有多条取用路径，控制器签名也与上下文 API 绑定。

重构后取参只保留注解一种写法，各段数据各有固定来源：

- 路径参数与查询参数在路由命中后统一装配为 `RequestParameter`，解码完成后交给参数绑定器，不再经上下文取数；
- 请求体按 `Content-Type` 解析为 `HttpFormContent` 或 `HttpJsonContent`，绑定器认具体内容类取数；
- 上下文的查询族与实体装载族方法全部下线，只保留请求消息、Session、Cookie、连接会话等请求级状态。

随这次收敛一起变化的行为：

| 项 | 旧行为 | 现行为 |
| --- | --- | --- |
| 查询串解码字符集 | 固定 UTF-8 | `HttpConfig` 的缺省字符集 |
| 路径或查询参数的百分号转义畸形 | 各处处理不一致 | 统一返回 400 |
| 上传文件注解 | 独立的 `@UploadParam` | 统一用 `@FormParam`，按形参类型判定 |
| 文件读取契约 | `HttpFile` | `MultipartFile` |

使用旧 API 的代码不能直接编译通过，需要改写为注解形参。

## 1. 取参方式总览

| 注解 | 取数位置 | 可声明的类型 |
| --- | --- | --- |
| `@PathParam` | 路由路径中的 `:name` 段 | 简单类型 |
| `@QueryParam` | URL 查询串 | 简单类型、`List<简单类型>`、`Set<简单类型>` |
| `@HeaderParam` | 请求头 | 简单类型 |
| `@CookieParam` | Cookie | 简单类型 |
| `@FormParam` | 表单字段或上传文件 | 简单类型、`MultipartFile`、`List<MultipartFile>`、`Set<MultipartFile>` |
| `@BeanQuery` | 整个查询串 | 任意 bean |
| `@BeanForm` | 整个表单体 | 任意 bean |
| `@BeanJson` | 整个 JSON 体 | 任意 bean |

简单类型指下面这些，取参与校验的转换口径一致：

`String`、`Integer`/`int`、`Long`/`long`、`Double`/`double`、`Boolean`/`boolean`、`LocalDate`、`LocalDateTime`、`LocalTime`。

数值按 `Integer.parseInt`、`Long.parseLong` 这类方法转换，布尔按 `Boolean.parseBoolean` 转换，三个时间类型按 ISO 格式调用 `parse`。声明成表外的类型时注解不会被识别，形参绑成 `null`。

框架对象的注入不需要注解，见本文最后一节。表里没有的取参方式可以自定义解析器，写法见[定制：参数解析器](./custom-parameter-parser.md)。

## 2. 路径参数

路径参数名必须与路由中的 `:name` 一致。路径写法的规则见[路由体系](./router.md)。

```java
package demo;

import top.turboweb.rest.annotation.binder.PathParam;
import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/order")
public class OrderController {

    @Get("/:orderId")
    public String detail(@PathParam("orderId") Long orderId) {
        return "order " + orderId;
    }
}
```

```bash
curl http://localhost:8080/order/1001
# order 1001
```

路径参数在路由命中时已经确定存在，不会有缺参的情况。值无法转换为声明类型时（例如 `Long` 形参收到 `abc`）抛运行时异常，默认按 500 处理。

路径参数按 `URLDecoder` 解码：百分号转义会被还原，加号 `+` 也会被还原成空格。需要传递字面加号时，发送 `%2B`。

## 3. 查询参数

`@QueryParam` 从 URI 的查询串取值，不读取请求体。

```java
@Route("/user")
public class UserController {

    @Get("/search")
    public String search(@QueryParam("keyword") String keyword,
                         @QueryParam("page") int page,
                         @QueryParam("tag") List<String> tags) {
        return keyword + ", page " + page + ", tags " + tags;
    }
}
```

```bash
curl "http://localhost:8080/user/search?keyword=turbo&page=2&tag=a&tag=b"
# turbo, page 2, tags [a, b]
```

取数规则：

| 形参声明 | 规则 |
| --- | --- |
| 单值 | 同名参数出现多次时取第一个 |
| `List<T>` | 按出现顺序保留全部取值 |
| `Set<T>` | 保留全部取值并去重 |
| 缺参 | `String` 与包装类型为 `null`；`int`/`long`/`double` 为 `0`；`boolean` 为 `false`；集合为空集合 |
| `?flag`（无等号） | 取值为 `null`，单值形参按缺参处理 |

缺省值没有注解参数可设。需要默认值时使用包装类型判空，或者用下一节的 `@BeanQuery` 在字段上直接赋初始值。

**_必填_**

`nonNull = true` 表示单值形参的绑定结果为 `null` 时报错。原始类型缺参时得到 `0` 或 `false`，不是 `null`，因此不会被这项检查拦下：

```java
@Get("/search")
public String search(@QueryParam(value = "keyword", nonNull = true) String keyword) {
    return keyword;
}
```

缺参时抛 `RouteHandlerException`，错误码 `MissingParamError`，响应 400。集合形参不做这项检查。

**_解码_**

查询串按 `HttpConfig` 的缺省字符集做百分号解码，`+` 保持字面值，不视为空格。转义不完整（例如 `%zz`、末尾单个 `%`）时返回 400。

## 4. 请求头

```java
@Get("/profile")
public String profile(@HeaderParam("X-Request-Id") String requestId,
                      @HeaderParam("User-Agent") String userAgent) {
    return requestId + " " + userAgent;
}
```

头名大小写不敏感，同名头有多个时取第一个。头未发送时按缺参处理：引用类型为 `null`，原始类型为 `0` 或 `false`。

## 5. Cookie

```java
@Get("/me")
public String me(@CookieParam("uid") Long uid) {
    return "uid " + uid;
}
```

Cookie 名称按原样精确匹配，取请求携带的值。名称不存在时包装类型为 `null`，基本类型为零值。响应侧写 Cookie 的用法见 [Cookie 管理](./cookie.md)。

## 6. 表单字段

`@FormParam` 同时用于表单字段和上传文件，声明类型决定走哪条通道。

```java
@Post("/signup")
public String signup(@FormParam("username") String username,
                     @FormParam("age") int age) {
    return username + " " + age;
}
```

```bash
curl -X POST http://localhost:8080/user/signup \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=zhangsan&age=20"
```

规则：

- 请求体必须是 `application/x-www-form-urlencoded` 或 `multipart/form-data`，否则抛 `RequestParseException`；
- 同名字段出现多次时只取第一个；
- `multipart/form-data` 中的普通文本字段与 urlencoded 字段同样处理；
- 缺字段时按缺参处理，原始类型取 `0` 或 `false`。

## 7. 上传文件

形参声明为 `MultipartFile`，或声明为元素类型是 `MultipartFile` 的 `List`、`Set` 时，`@FormParam` 按上传文件绑定，注解的值就是表单字段名。

```java
package demo;

import top.turboweb.rest.annotation.binder.FormParam;
import top.turboweb.rest.annotation.router.Post;
import top.turboweb.rest.annotation.router.Route;
import top.turboweb.rest.core.MultipartFile;

import java.io.IOException;
import java.util.List;

@Route("/user")
public class UserController {

    @Post("/avatar")
    public String upload(@FormParam("avatar") MultipartFile avatar,
                         @FormParam("attachments") List<MultipartFile> attachments) throws IOException {
        if (avatar == null || avatar.isEmpty()) {
            return "no avatar";
        }
        System.out.println(avatar.filename() + ": " + avatar.length() + " bytes");
        System.out.println("attachments: " + attachments.size());
        byte[] bytes = avatar.readAll();
        return "uploaded " + bytes.length;
    }
}
```

```bash
curl -X POST http://localhost:8080/user/avatar \
  -F "avatar=@./me.png" \
  -F "attachments=@./a.txt" \
  -F "attachments=@./b.txt"
```

`MultipartFile` 的读取接口：

| 方法 | 说明 |
| --- | --- |
| `filename()` | 浏览器提交的原始文件名，未提供时为 `null` |
| `contentType()` | 该文件声明的类型，未声明时为 `null` |
| `length()` | 内容字节数 |
| `isEmpty()` | 内容长度为 0 时为 `true` |
| `inputStream()` | 自起始处打开输入流，每次调用互不影响 |
| `readAll()` | 读取全部内容，返回字节数组 |

文件数据由框架随请求处理结束统一释放，`MultipartFile` 只在本次请求内有效。需要跨请求保存时，在处理结束前通过 `readAll()` 或 `inputStream()` 复制出去。

同一字段没有文件时：单个形参为 `null`，`List`、`Set` 为空集合。声明为裸集合、`Object` 或通配符元素类型时不会按文件绑定，形参得到 `null`；元素类型不是 `MultipartFile` 的集合同样不按文件绑定。集合形态只支持 `List` 与 `Set`，声明为其他集合类型时构建路由表直接拒绝。

## 8. 查询参数映射为对象

查询参数多时，逐个写 `@QueryParam` 很啰嗦。`@BeanQuery` 把整个查询串绑定为一个 bean。

```java
public class PageQuery {
    private int pageNum = 1;
    private int pageSize = 10;

    // getter / setter 省略
}
```

```java
@Get("/list")
public String list(@BeanQuery PageQuery query) {
    return "page " + query.getPageNum() + ", size " + query.getPageSize();
}
```

```bash
curl "http://localhost:8080/user/list?pageNum=3"
# page 3, size 10
```

参数名与属性名一致即完成映射，未出现的参数保留字段的初始值。绑定经应用配置的 `JsonSerializer` 完成，Bean 的属性类型按其转换能力处理。替换与配置写法见[定制：序列化与实体校验](./custom-serializer-validation.md)。

同名参数折叠规则：只有一个值时以字符串传入，多个值时以列表传入。因此属性声明要与实际形状匹配，例如多值参数对应 `List<String>` 字段；只传一个值时仍按单值折叠，列表字段会转换失败。

## 9. 表单映射为对象

```java
public class UserForm {
    private String username;
    private String nickname;
    private Integer age;

    // getter / setter 省略
}
```

```java
@Post("/create")
public String create(@BeanForm UserForm form) {
    return form.getUsername() + " " + form.getNickname();
}
```

```bash
curl -X POST http://localhost:8080/user/create \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=zhangsan&nickname=张三&age=20"
```

请求体不是表单类型时抛 `RequestParseException`。字段映射与 `@BeanQuery` 相同，同样经 `JsonSerializer` 完成。

## 10. JSON 映射为对象

```java
@Post("/json")
public String json(@BeanJson UserForm form) {
    return form.getUsername() + " " + form.getAge();
}
```

```bash
curl -X POST http://localhost:8080/user/json \
  -H "Content-Type: application/json" \
  -d '{"username":"lisi","nickname":"李四","age":18}'
```

约束：

- `Content-Type` 必须是 `application/json`，否则抛 `RequestParseException`；
- 请求体为空时按 `{}` 处理，bean 全部保留初始值；
- 请求体去掉首尾空白后必须由花括号包裹，数组、字符串等非对象体抛 `RequestParseException`；
- 体内部结构不合法（例如 `{"age":}`）由序列化器报错，默认按 500 处理；
- 未知字段按序列化器的配置跳过，不会因为多传字段而失败。

## 11. 参数校验

`@BeanQuery`、`@BeanForm`、`@BeanJson` 都支持 Jakarta Bean Validation（JSR-380）：

```java
public class UserForm {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @Min(value = 1, message = "年龄必须大于 0")
    private Integer age;

    // getter / setter 省略
}
```

注解的 `value` 打开校验，默认关闭：

```java
@Post("/create")
public String create(@BeanForm(true) UserForm form) {
    return form.getUsername();
}
```

分组校验通过 `groups` 指定：

```java
public interface Groups {
    interface Add {}
    interface Update {}
}

@Post("/create")
public String create(@BeanForm(value = true, groups = Groups.Add.class) UserForm form) {
    return form.getUsername();
}
```

校验失败抛 `BeanValidationException`，异常按字段承载全部违规信息，`failMessages()` 返回失败消息列表。校验发生在调用目标方法之前，业务方法里捕不到，需要统一在异常处理器中处理：

```java
public class ValidationExceptionHandler {

    public HttpResult<String> handle(BeanValidationException e) {
        return HttpResult.create(400, String.join("; ", e.failMessages()));
    }
}
```

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .exceptionHandlers(new ValidationExceptionHandler())
        .routeRegister(routeRegister)
        .build();
```

不注册处理器时走兜底逻辑，返回 500。异常处理器的匹配规则见[异常处理器](./exceptionhandler.md)。

默认校验器是 Hibernate Validator。需要替换时在生成路由表之前指定：

```java
RestRouteRegister routeRegister = new RestRouteRegister()
        .beanValidator(myBeanValidator)
        .registerRoute(new UserController());
```

校验器的契约、默认实现与自定义写法见[定制：序列化与实体校验](./custom-serializer-validation.md)。

## 12. 框架对象注入

形参声明下列类型时不需要注解，框架直接注入：

| 类型 | 含义 |
| --- | --- |
| `HttpContext` | 当前请求上下文 |
| `HttpSession` | 当前会话 |
| `HttpCookies` | 当前请求的 Cookie 集合 |
| `SseResponse` | SSE 响应对象，按本次请求构造 |
| `SseEmitter` | SSE 发射器，按本次请求构造 |
| `ConnectSession` | 当前连接会话 |
| `Channel` | 当前连接的 Netty 通道 |

```java
@Get("/context")
public String context(HttpContext ctx, HttpSession session, HttpCookies cookies) {
    session.setAttr("lastPath", ctx.message().url().url(true));
    return cookies.getCookie("uid");
}
```

注入按形参声明类型精确匹配，不做子类匹配。`HttpContext` 上保留 `message()`、`httpSession()`、`cookies()`、`getConnectSession()`、`defaultCharset()`、`attachment()`，不再提供取参方法。

## 13. 请求体与字符集

请求体在进入控制器之前已按 `Content-Type` 解析：

| `Content-Type` | 内容类 | 可读取 |
| --- | --- | --- |
| `application/x-www-form-urlencoded`、`multipart/form-data` | `HttpFormContent` | `formParams()`、`formFiles()` |
| `application/json` | `HttpJsonContent` | `jsonString()` |
| 其他或未声明 | `HttpUnknowContent` | 仅 `contentType()` |

其他 `Content-Type` 可以通过自定义内容解析器接入，写法见[定制：参数解析器](./custom-parameter-parser.md)第 3 节。

需要绕开注解直接读体时，经 `ctx.message().content()` 转型取数：

```java
@Post("/raw")
public String raw(HttpContext ctx) {
    HttpJsonContent json = (HttpJsonContent) ctx.message().content();
    return json.jsonString();
}
```

字符集规则：

- 请求体按 `Content-Type` 中声明的 `charset` 解码，未声明时回落到 `HttpConfig` 的缺省字符集；
- 路径参数与查询参数不受请求头影响，一律按 `HttpConfig` 的缺省字符集解码。

## 14. 出错时的响应

| 情形 | 默认结果 |
| --- | --- |
| 路径或查询参数百分号转义畸形 | 400，`ParamDecodeError` |
| `@QueryParam(nonNull = true)` 缺值 | 400，`MissingParamError` |
| 声明类型转换失败（如 `Long` 收到 `abc`） | 运行时异常，500 |
| `@FormParam`/`@BeanForm`/`@BeanJson` 与请求体类型不符 | `RequestParseException`，500 |
| 校验不通过 | `BeanValidationException`，500 |
| 形参没有解析器认领 | 绑成 `null`；原始类型在调用时拆箱失败，500 |

后四类可以通过注册对应的异常处理器改成业务需要的状态码与响应体，见[异常处理器](./exceptionhandler.md)。

[首页](../README.md) | [路由体系](./router.md) | [响应体系](./response.md)
