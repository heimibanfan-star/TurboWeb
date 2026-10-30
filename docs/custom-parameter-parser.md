# 定制：参数解析器

控制器方法的形参怎么取值，由参数解析器决定。框架内置了路径、查询、表单、JSON、请求头、Cookie 等九种取参方式，覆盖不到的业务写法可以自己补一个解析器。本文说明解析器在什么时机工作、怎样注册，以及遇到新的请求体格式时怎样和内容解析器配合。

## 1. 解析器的两个阶段

| 阶段 | 方法 | 时机 | 要做的事 |
| --- | --- | --- | --- |
| 装配期 | `ParameterParser#parse(Parameter)` | 每个形参调用一次，发生在路由表生产时 | 判断认不认领这个形参；认领就返回绑定器，不认领返回 `null` |
| 请求期 | `Binder#bindParameter(...)` | 每次请求、调用控制器之前 | 从上下文取数、转换类型，返回实参 |

装配发生在 `RestRouteRegister#newRouteHandlerManager`，也就是 `HttpRequestApplication.Builder#build()` 的时候。解析器按表中的顺序逐个尝试，第一个返回非 `null` 的生效；全部返回 `null` 时，该形参绑成 `null`，原始类型形参会因为拆箱失败按 500 处理。

内置解析器与尝试顺序：

| 顺序 | 解析器 | 认领的形参 |
| --- | --- | --- |
| 1 | `InternTypeParameterParser` | 框架对象，按声明类型精确匹配 |
| 2 | `PathParameterParser` | `@PathParam` 加简单类型 |
| 3 | `QueryParameterParser` | `@QueryParam` 加简单类型、`List`、`Set` |
| 4 | `BeanQueryParameterParser` | `@BeanQuery` |
| 5 | `BeanFormParameterParser` | `@BeanForm` |
| 6 | `BeanJsonParameterParser` | `@BeanJson` |
| 7 | `CookieParameterParser` | `@CookieParam` 加简单类型 |
| 8 | `FormParameterParser` | `@FormParam` 加简单类型、`MultipartFile` 及其集合 |
| 9 | `HeaderParameterParser` | `@HeaderParam` 加简单类型 |

简单类型指 `String`、`Integer`/`int`、`Long`/`long`、`Double`/`double`、`Boolean`/`boolean`、`LocalDate`、`LocalDateTime`、`LocalTime`。这张表是固定的，没有注册入口；要支持别的类型，写一个参数解析器自己转换。

`Binder` 的四个入参：

```java
Object bindParameter(HttpContext ctx,
                     RequestParameter requestParameter,
                     BeanValidator beanValidator,
                     JsonSerializer jsonSerializer);
```

- `ctx`：当前请求上下文，可拿消息、Session、Cookie、连接会话、请求体内容；
- `requestParameter`：路径参数与查询参数，已按缺省字符集解码；
- `beanValidator`：路由登记表上配置的校验器；
- `jsonSerializer`：应用配置的序列化器，替换与配置写法见[定制：序列化与实体校验](./custom-serializer-validation.md)。

后面三个都不用时直接忽略。路由侧每次调用都会把同一组对象传给绑定器。

## 2. 简单定制：补一个取参方式

举个实际会遇到的缺口：`@QueryParam` 不认识枚举，声明成枚举的形参会绑成 `null`。加一个枚举解析器即可。

先定义注解。参数上的注解必须保留到运行时：

```java
package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumParam {
    String value();
}
```

再写解析器。继承 `AbstractParameterParser` 只需要实现 `doParse`，`parse` 已经被固定为直接委派：

```java
package demo;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.parameter.AbstractParameterParser;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.RequestParameter;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;

public class EnumParameterParser extends AbstractParameterParser {

    private record EnumBinder(String name, Class<?> type) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter,
                                    BeanValidator beanValidator, JsonSerializer jsonSerializer) {
            String raw = requestParameter.queryParam(name);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(raw)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("unknown enum value: " + raw + " for " + type.getName());
        }
    }

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(EnumParam.class)) {
            return null;
        }
        Class<?> type = parameter.getType();
        if (!type.isEnum()) {
            return null;
        }
        return new EnumBinder(parameter.getAnnotation(EnumParam.class).value(), type);
    }
}
```

`doParse` 只做结构判断：有没有注解、类型对不对。它不接触请求，所以一个解析器实例可以在所有控制器之间共享。真正取数放在 `bindParameter` 里。

控制器：

```java
package demo;

import top.turboweb.rest.annotation.router.Get;
import top.turboweb.rest.annotation.router.Route;

@Route("/order")
public class OrderController {

    public enum OrderStatus {
        CREATED, PAID, SHIPPED
    }

    @Get("/list")
    public String list(@EnumParam("status") OrderStatus status) {
        return status == null ? "all" : status.name();
    }
}
```

注册到路由登记表：

```java
RestRouteRegister routeRegister = new RestRouteRegister()
        .registerParameterParserLast(new EnumParameterParser())
        .registerRoute(new OrderController());

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .build();
```

```bash
curl "http://localhost:8080/order/list?status=PAID"
# PAID
```

传了表里没有的值会抛 `IllegalArgumentException`，默认按 500 处理。要改成 400，注册一个异常处理器转换状态码即可，写法见[异常处理器](./exceptionhandler.md)。

**_注册位置_**

| 方法 | 效果 |
| --- | --- |
| `registerParameterParserFirst(parser)` | 插到表头，最先被尝试 |
| `registerParameterParserLast(parser)` | 插到表尾，最后被尝试 |
| `registerParameterParserBefore(target, parser)` | 插到同类目标之前 |
| `registerParameterParserAfter(target, parser)` | 插到同类目标之后 |
| `registerParameterParser(target, parser)` | 原位替换同类目标 |
| `removeParameterParser(parser...)` | 按类剔除 |

`target` 按 class 定位，所以传入的实例只用来指定位置，不必是表里那一个实例。位置只在需要抢在内置解析器之前时才有意义：例如你的解析器要接管的注解和内置解析器相同，就必须放在它前面，否则轮不到。

注册规则：

- 同一个 class 不能注册两次，重复注册抛 `IllegalStateException`；
- 目标解析器不在表里时抛 `IllegalArgumentException`；
- 路由登记表一经生产就冻结，之后再调用上述方法抛 `IllegalStateException`。换句话说，所有调整都要在 `HttpRequestApplication.Builder#build()` 之前完成；
- 解析器的剔除、定位都按 class 比较，不同实例只要类不同就互不干扰。

## 3. 复杂定制：内容解析器与参数解析器配合

请求体在路由匹配之前就已经解析好了。链路是：

1. 请求进入编排器，`HttpContentParserManager` 按 `Content-Type` 选一个 `HttpContentParser`；
2. 内容解析器把请求体解析成 `HttpContent`，放进请求消息；
3. 路由命中后，参数解析器的绑定器从 `ctx.message().content()` 取这个内容对象，转成形参。

所以新请求体格式需要两半：内容解析器负责“体变成什么”，参数解析器负责“怎么取出来”。

**_内容解析器的契约_**

```java
public interface HttpContentParser {

    Set<ContentType> contentTypes();

    HttpContent parse(FullHttpRequest request, Charset defaultCharset);
}
```

- `contentTypes()` 至少返回一个类型，一个解析器可以声明多个 MIME 类型；
- `parse` 必须在本次调用内把请求体读完。编排流程在装配完消息后立即释放请求对象，实现不能持有请求引用、也不能返回后再读体；
- 产出的内容对象只在本次请求内使用。持有直接内存或磁盘临时文件的实现应实现 `AutoCloseContent`，由编排器在请求结束后调用 `close()`；
- 类型匹配不区分大小写，`charset` 等参数段不参与匹配。

`HttpContentParserManager` 的查找规则：

- 先查内置解析器，内置只有表单（`application/x-www-form-urlencoded`、`multipart/form-data`）与 JSON（`application/json`）；
- 内置没命中再查自定义表；
- 自定义解析器不能声明内置类型，也不能与其他自定义解析器重复声明同一类型，冲突在管理器构造时抛 `IllegalArgumentException`；
- 请求头没有 `Content-Type`、或类型无人认领时，内容对象是 `HttpUnknowContent`，它只带类型，没有请求体；参数解析器拿不到原始字节；
- `HEAD` 请求没有请求体，不进入内容解析器，内容恒为未声明类型的 `HttpUnknowContent`。

下面以 `text/csv` 为例，写完整的两半。

```java
package demo;

import top.turboweb.rest.core.ContentType;
import top.turboweb.rest.core.HttpContent;

import java.util.List;

public final class CsvContent implements HttpContent {

    private final ContentType contentType;
    private final List<List<String>> rows;

    public CsvContent(ContentType contentType, List<List<String>> rows) {
        this.contentType = contentType;
        this.rows = rows;
    }

    public List<List<String>> rows() {
        return rows;
    }

    @Override
    public ContentType contentType() {
        return contentType;
    }
}
```

```java
package demo;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.http.orchestrator.parser.HttpRequestHelper;
import top.turboweb.http.orchestrator.parser.HttpContentParser;
import top.turboweb.rest.core.ContentType;
import top.turboweb.rest.core.HttpContent;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

public final class CsvContentParser implements HttpContentParser {

    private static final ContentType TEXT_CSV = ContentType.create("text/csv");

    @Override
    public Set<ContentType> contentTypes() {
        return Set.of(TEXT_CSV);
    }

    @Override
    public HttpContent parse(FullHttpRequest request, Charset defaultCharset) {
        Charset charset = HttpRequestHelper.getRequestCharset(request, defaultCharset);
        String body = request.content().toString(charset);
        List<List<String>> rows = body.lines()
                .filter(line -> !line.isBlank())
                .map(line -> List.of(line.split(",", -1)))
                .toList();
        return new CsvContent(ContentType.create("text/csv", charset), rows);
    }
}
```

参数解析器只认内容对象：

```java
package demo;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.router.parameter.AbstractParameterParser;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.RequestParameter;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CsvBody {
}

public class CsvBodyParameterParser extends AbstractParameterParser {

    private static final ParameterParser.Binder BINDER =
            (ctx, requestParameter, beanValidator, jsonSerializer) -> {
                HttpContent content = ctx.message().content();
                if (!(content instanceof CsvContent csv)) {
                    throw new RequestParseException("contentType is not text/csv");
                }
                return csv;
            };

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        return parameter.isAnnotationPresent(CsvBody.class) ? BINDER : null;
    }
}
```

控制器方法（加在 `OrderController` 中）：

```java
@Post("/import")
public String importOrders(@CsvBody CsvContent csv) {
    return "rows " + csv.rows().size();
}
```

**_注册_**

内容解析器和参数解析器一样在构建前注册，两者各自登记到应用与路由登记表：

```java
RestRouteRegister routeRegister = new RestRouteRegister()
        .registerParameterParserLast(new CsvBodyParameterParser())
        .registerRoute(new OrderController());

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .contentParsers(new CsvContentParser())
        .routeRegister(routeRegister)
        .build();
```

```bash
curl -X POST http://localhost:8080/order/import \
  -H "Content-Type: text/csv" \
  --data-binary $'orderNo,amount\nA1001,99'
# rows 2
```

注册规则：

- `contentParsers` 是可变参数，可以一次传多个解析器；一个解析器也可以在 `contentTypes()` 里声明多个 MIME 类型；
- 声明的类型与内置类型（表单两种、`application/json`）冲突，或两个自定义解析器声明同一类型，在 `build()` 时抛 `IllegalArgumentException`；
- 注册只在 `build()` 之前有效，构建后再调用不会进入已装配的管理器；
- 请求头没有 `Content-Type`、或类型无人认领时，内容对象是 `HttpUnknowContent`，参数解析器拿不到原始字节。

**_另一条路：复用内置内容解析器_**

如果只是想在已有的表单或 JSON 体里再做一层解析，不需要新的 MIME 类型，就不必写内容解析器。参数解析器可以直接从内置的 `HttpFormContent`、`HttpJsonContent` 取数。例如表单里放一个 JSON 字段：

```java
package demo;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpFormContent;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.router.parameter.AbstractParameterParser;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.RequestParameter;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.List;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface FormJson {
    String value();
}

public class FormJsonParameterParser extends AbstractParameterParser {

    private record FormJsonBinder(String field, Class<?> type) implements ParameterParser.Binder {

        @Override
        public Object bindParameter(HttpContext ctx, RequestParameter requestParameter,
                                    BeanValidator beanValidator, JsonSerializer jsonSerializer) {
            HttpContent content = ctx.message().content();
            if (!(content instanceof HttpFormContent form)) {
                throw new RequestParseException(
                        "contentType is not application/x-www-form-urlencoded or multipart/form-data");
            }
            List<String> values = form.formParams().get(field);
            String json = values == null || values.isEmpty() ? null : values.getFirst();
            if (json == null || json.isBlank()) {
                return null;
            }
            return jsonSerializer.jsonToBean(json, type);
        }
    }

    @Override
    protected ParameterParser.Binder doParse(Parameter parameter) {
        if (!parameter.isAnnotationPresent(FormJson.class)) {
            return null;
        }
        return new FormJsonBinder(parameter.getAnnotation(FormJson.class).value(), parameter.getType());
    }
}
```

```java
@Post("/checkout")
public String checkout(@FormJson("payload") CheckoutForm payload) {
    return payload.getOrderNo();
}
```

```bash
curl -X POST http://localhost:8080/checkout \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode 'payload={"orderNo":"A1001","amount":99}'
```

这里内容解析器仍是内置的表单解析器，参数解析器在它产出的字段上再做一次 JSON 反序列化。只有在没有合适的内置内容类时，才需要按上一节补一个内容解析器。

## 4. 编写约定

- `doParse` 只做静态判断，不取请求数据；请求期逻辑全部放在 `bindParameter`。
- 不认领的形参必须返回 `null`，不要抛异常。解析器按顺序都会被问到，抛异常会让无关的形参也失败。
- 绑定器不要持有 `ctx`、`RequestParameter` 或内容对象的引用。请求处理结束后内容对象会被关闭，上传文件等资源随之失效。
- 绑定结果为 `null` 时形参就是 `null`；原始类型形参随后拆箱失败，按 500 处理。需要必填时在绑定器里自行判断并抛异常。
- 参数错误用 `RouteHandlerException` 的 `ParamDecodeError` 或 `MissingParamError`，对应 400；`RequestParseException` 与类型转换异常按 500 处理。要换状态码，注册异常处理器。
- 新标量类型不要试图改 `ParameterHandleHelper`，它是静态表，没有注册入口，在自定义绑定器里转换即可。
- 内容解析器必须在 `parse` 内读完请求体，不能缓存 `FullHttpRequest`；持有资源的 `HttpContent` 实现 `AutoCloseContent`。
- 参数解析器在应用构建前注册。构建完成后再改登记表会抛 `IllegalStateException`。
- 内容解析器同样在构建前经 `HttpRequestApplication.Builder#contentParsers` 注册；类型冲突在构建时抛出，不会静默替换。

[首页](../README.md) | [请求体系](./request.md) | [路由体系](./router.md)
