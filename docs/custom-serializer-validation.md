# 定制：序列化与实体校验

请求侧与响应侧的 JSON 编解码统一收在 `top.turboweb.serializer.JsonSerializer` 后面，Bean 形参的校验统一收在 `top.turboweb.validation.BeanValidator` 后面。控制器返回对象、`HttpResult` 的非字符串正文、流中的对象元素，以及 `@BeanQuery`、`@BeanForm`、`@BeanJson` 的参数绑定都经过序列化器；带校验开关的 Bean 形参在绑定之后、调用控制器之前经过校验器。默认实现分别是基于 Jackson 的 `JacksonJsonSerializer` 与基于 Hibernate Validator 的 `HibernateBeanValidator`。

替换这两个实例，就能统一改变读写 JSON 的口径与校验行为，而不用逐个修改控制器。本文说明它们接管哪些路径、接口契约、默认实现的配置，以及替换方式。这里只针对 HTTP 服务端应用，客户端模块的 JSON 转换不由这个入口配置。

## 1. 序列化器接管哪些路径

| 场景 | 调用的方法 |
| --- | --- |
| 控制器返回普通对象，由 JSON 兜底转化器处理 | `beanToJson` |
| `HttpResult` 的 `data` 不是 `String` | `beanToJson` |
| `Publisher` 流中的对象元素 | `beanToJson` |
| `@BeanQuery`、`@BeanForm` 绑定 bean | `mapToBean` |
| `@BeanJson` 绑定 bean | `jsonToBean` |

几点边界：

- 响应正文由序列化器产出字符串，再按 `HttpConfig` 的缺省字符集编码。序列化器不接触字符集，也不应把字符集写进结果。
- `String`、`HttpFileResult`、`HttpResponse`、`null` 都不经序列化器；流中的 `String`、`Number`、`byte[]`、`ByteBuffer`、`ByteBuf` 元素直接编码，只有其他对象才交给序列化器。
- 路径、查询、表单、请求头、Cookie 的简单类型转换走固定转换表，不受序列化器影响。
- 构建期只装配一个实例：`HttpRequestApplication.Builder#build()` 把建造者的序列化器交给结果转化器管理器；挂了路由登记表时也交给它生产路由处理器管理器。响应侧与参数绑定侧共用同一个对象，`jsonSerializer(...)` 与 `routeRegister(...)` 的调用顺序不影响结果。

序列化过程抛出的运行时异常会离开转化或绑定环节，交由异常处理器处置，默认返回 500。需要换状态码或响应体时注册异常处理器即可。

## 2. 契约

```java
public interface JsonSerializer {

    <T> T mapToBean(Map<String, Object> map, Class<T> beanClass);

    Map<?, ?> beanToMap(Object bean);

    String beanToJson(Object bean);

    <T> T jsonToBean(String json, Class<T> beanClass);

    Map<?, ?> jsonToMap(String json);

    <T> List<T> jsonToList(String json, Class<T> beanClass);

    <T> T deepinClone(T bean);

    <T> T jsonUpdateBean(String json, T bean);
}
```

服务器内部只调用其中三个：`beanToJson`、`mapToBean`、`jsonToBean`。其余五个方法仍然属于接口契约，应用代码和客户端模块会用到，实现时不能留空。

两个容易踩的点：

- `mapToBean` 的入参不是 JSON 解析出来的树，而是查询串或表单折叠后的映射：值要么是 `String`，要么是 `List<String>`。只接受 JSON 原生类型的实现会在 `@BeanQuery`、`@BeanForm` 上失败。
- 所有方法都是同步调用，失败时抛运行时异常。框架自带的异常类型是 `SerializerException`，换库时把自己库的受检异常包进去，错误类型才稳定。

## 3. 默认实现与配置

默认实例是 `new JacksonJsonSerializer()`。它的构造器决定了一部分行为：

| 构造器 | 行为 |
| --- | --- |
| `JacksonJsonSerializer()` | 注册 JavaTimeModule，时间为固定格式；忽略未知字段 |
| `JacksonJsonSerializer(Consumer<ObjectMapper>)` | 先注册同样的时间模块，再执行回调；回调结束后仍强制忽略未知字段 |
| `JacksonJsonSerializer(Module...)` | 注册传入的模块，忽略未知字段；不自动注册 JavaTimeModule |
| `JacksonJsonSerializer(ObjectMapper)` | 完全使用传入的 mapper，不注册时间模块，也不改未知字段开关 |

不带参数的构造器装的时间格式：

| 类型 | 格式 |
| --- | --- |
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` |
| `LocalDate` | `yyyy-MM-dd` |
| `LocalTime` | `HH:mm:ss` |
| `Instant` | ISO-8601 |

`Consumer<ObjectMapper>` 构造器在回调之后才把 `FAIL_ON_UNKNOWN_PROPERTIES` 设为 `false`，因此在回调里打开它不会生效。需要严格拒绝未知字段时，只能用 `ObjectMapper` 构造器自行配置。

## 4. 替换方式

### 4.1 调整 Jackson 配置

多数替换只是改序列化口径，不必重写实现。下面的实例把属性名改成下划线风格，并省略值为 `null` 的属性：

```java
JacksonJsonSerializer serializer = new JacksonJsonSerializer(mapper -> {
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
});
```

注册到应用：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .jsonSerializer(serializer)
        .routeRegister(routeRegister)
        .build();
```

控制器与响应：

```java
public record User(String userName, int age, String nickname) {
}

@Get("/user")
public User user() {
    return new User("张三", 18, null);
}
```

```bash
curl -i http://localhost:8080/user
# HTTP/1.1 200 OK
# Content-Type: application/json;charset=UTF-8
#
# {"user_name":"张三","age":18}
```

反序列化同样生效。`@BeanJson` 收到下划线字段名也能绑定：

```java
@Post("/user")
public String create(@BeanJson User user) {
    return user.userName() + " " + user.age();
}
```

```bash
curl -X POST http://localhost:8080/user \
  -H "Content-Type: application/json" \
  -d '{"user_name":"李四","age":20}'
# 李四 20
```

要改时间格式，在回调里注册新的 `LocalDateTime` 序列化器与反序列化器覆盖默认值：

```java
JacksonJsonSerializer serializer = new JacksonJsonSerializer(mapper -> {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    SimpleModule module = new SimpleModule();
    module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
    module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
    mapper.registerModule(module);
});
```

### 4.2 换成其他 JSON 库

换库时实现 `JsonSerializer` 的八个方法。下面用 Jackson 作后端给出完整实现，换成其他库时只替换方法体：

```java
package demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.serializer.exception.SerializerException;

import java.util.List;
import java.util.Map;

public final class CustomJsonSerializer implements JsonSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
        return mapper.convertValue(map, beanClass);
    }

    @Override
    public Map<?, ?> beanToMap(Object bean) {
        return mapper.convertValue(bean, Map.class);
    }

    @Override
    public String beanToJson(Object bean) {
        try {
            return mapper.writeValueAsString(bean);
        } catch (JsonProcessingException e) {
            throw new SerializerException(e);
        }
    }

    @Override
    public <T> T jsonToBean(String json, Class<T> beanClass) {
        try {
            return mapper.readValue(json, beanClass);
        } catch (JsonProcessingException e) {
            throw new SerializerException(e);
        }
    }

    @Override
    public Map<?, ?> jsonToMap(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new SerializerException(e);
        }
    }

    @Override
    public <T> List<T> jsonToList(String json, Class<T> beanClass) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, beanClass));
        } catch (JsonProcessingException e) {
            throw new SerializerException(e);
        }
    }

    @Override
    public <T> T deepinClone(T bean) {
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) bean.getClass();
        return jsonToBean(beanToJson(bean), beanClass);
    }

    @Override
    public <T> T jsonUpdateBean(String json, T bean) {
        try {
            return mapper.readerForUpdating(bean).readValue(json);
        } catch (JsonProcessingException e) {
            throw new SerializerException(e);
        }
    }
}
```

注册方式与上一节相同：

```java
HttpRequestApplication application = new HttpRequestApplication.Builder()
        .jsonSerializer(new CustomJsonSerializer())
        .routeRegister(routeRegister)
        .build();
```

只调整 Jackson 的行为时不需要写这个类，直接用上一节的 `JacksonJsonSerializer(Consumer<ObjectMapper>)` 更省事。

## 5. 生效范围与注册

`HttpRequestApplication.Builder#jsonSerializer(JsonSerializer)` 接收一个实例，传 `null` 在调用时抛 `NullPointerException`。

- 装配发生在 `build()`：结果转化器管理器与路由处理器管理器都从建造者取这同一个实例，之后无法替换。要换序列化器，重新构建应用。
- 序列化器全应用共享，实现必须线程安全。`ObjectMapper` 配置完成后可以并发使用，但不要在运行期继续改它的配置。
- 只影响当前应用，不同应用可以用不同的序列化器。
- 自定义结果转化器由调用方自行构造，应用不会把序列化器注入进去；需要序列化时在构造器里传入。自定义参数解析器的绑定器则会在每次调用时收到应用序列化器与路由登记表上的校验器。
- 缺省字符集由 `HttpConfig` 单独配置。序列化器只产出字符串，不负责编码。
- `HttpResult` 的 `data` 为 `null` 时同样交给序列化器，Jackson 的结果是字符串 `null`。替换实现若返回空串或抛异常，响应行为会随之改变。

## 6. Bean 校验

`@BeanQuery`、`@BeanForm`、`@BeanJson` 绑定出的 Bean 可以顺带做 JSR-380 校验。开关在参数注解上，执行校验的是路由登记表上配置的 `BeanValidator`。

### 6.1 校验的触发时机

```java
@Post("/user")
public String create(@BeanJson(value = true, groups = Groups.Add.class) UserForm form) {
    return form.getUsername();
}
```

| 注解 | 开关字段 | 分组字段 |
| --- | --- | --- |
| `@BeanQuery` | `value` | `groups` |
| `@BeanForm` | `value` | `groups` |
| `@BeanJson` | `value` | `groups` |

- `value` 默认 `false`，不开校验时绑定器不调用校验器；
- `groups` 为空时调用 `validate(bean)`，由引擎按默认分组执行；非空时调用 `validate(bean, groups)`；
- 校验发生在参数绑定阶段，也就是控制器方法被调用之前，方法体里的 `try...catch` 捕不到校验异常；
- 校验失败抛 `BeanValidationException`，默认按 500 响应。注册异常处理器后可以改成 400 并自定义响应体，写法见[异常处理器](./exceptionhandler.md)。

### 6.2 校验器的契约

```java
public interface BeanValidator extends AutoCloseable {

    void validate(Object bean);

    void validate(Object bean, Class<?>... groups);

    @Override
    void close();
}
```

- 两个 `validate` 都没有返回值，违规即抛异常；
- 异常必须承载全部违规（非 fail-fast），异常处理器经 `failMessages()` 一次性拿到所有失败消息；
- `bean` 为 `null` 时抛 `IllegalArgumentException`，两个重载都如此；
- `close()` 收窄为不抛受检异常，持有引擎资源的实现可以在其中释放；无资源可释放时留空。

### 6.3 默认实现

没有调用 `beanValidator(...)` 时，路由处理器使用 `RestRouteRegister` 内的静态默认实例 `new HibernateBeanValidator()`。两种构造：

| 构造器 | 行为 |
| --- | --- |
| `HibernateBeanValidator()` | 经 `Validation.buildDefaultValidatorFactory()` 自举引擎，实例持有工厂，`close()` 时释放 |
| `HibernateBeanValidator(Validator validator)` | 直接使用外部引擎，不持有工厂，`close()` 不作动作 |

Hibernate 的 `Validator` 线程安全，默认实例由所有未单独指定校验器的路由共享。无参构造要求运行时类路径上有 Hibernate Validator 与 EL 实现。

### 6.4 替换校验器

**_换引擎，不换契约_**

只想换引擎配置，例如消息插值器、自定义约束实现，或复用容器里的 `Validator` 实例时，直接把已有引擎交给 `HibernateBeanValidator`：

```java
ValidatorFactory factory = Validation.byProvider(HibernateValidator.class)
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory();

RestRouteRegister routeRegister = new RestRouteRegister()
        .beanValidator(new HibernateBeanValidator(factory.getValidator()))
        .registerRoute(new UserController());
```

外部注入的引擎归调用方所有，`close()` 不会关闭它，应用退出时由创建方释放 `factory`。

**_实现接口_**

需要改变校验行为时实现 `BeanValidator`。下面是一个以 JSR-380 为后端的完整实现：

```java
package demo;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import top.turboweb.validation.BeanValidationException;
import top.turboweb.validation.BeanValidator;

import java.util.Set;

public final class ProjectBeanValidator implements BeanValidator {

    private final ValidatorFactory factory;
    private final Validator validator;

    public ProjectBeanValidator() {
        this.factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    @Override
    public void validate(Object bean) {
        validate(bean, new Class<?>[0]);
    }

    @Override
    public void validate(Object bean, Class<?>... groups) {
        if (bean == null) {
            throw new IllegalArgumentException("bean must not be null");
        }
        Set<ConstraintViolation<Object>> violations =
                groups == null || groups.length == 0
                        ? validator.validate(bean)
                        : validator.validate(bean, groups);
        if (!violations.isEmpty()) {
            throw new BeanValidationException(violations);
        }
    }

    @Override
    public void close() {
        factory.close();
    }
}
```

这段实现与默认实现等价，用来说明契约。实际编写时可以在这里加入日志、指标或业务前置判断。接入 JSR-380 之外的校验体系时没有 `ConstraintViolation` 集合，构造不出 `BeanValidationException`，此时应抛出自己的运行时异常，并注册异常处理器把它转换为需要的响应。

注册到路由登记表：

```java
RestRouteRegister routeRegister = new RestRouteRegister()
        .beanValidator(new ProjectBeanValidator())
        .registerRoute(new UserController());

HttpRequestApplication application = new HttpRequestApplication.Builder()
        .routeRegister(routeRegister)
        .build();
```

注册规则：

- 校验器属于路由登记表，作用于该登记表生产出的全部路由；
- 必须在生产之前调用，生产由 `HttpRequestApplication.Builder#build()` 触发，之后再调用抛 `IllegalStateException`；
- 传 `null` 抛 `NullPointerException`；
- 一个应用只装配一个路由登记表，要换校验器就重建登记表与应用；
- 校验器随路由处理器常驻，实现必须线程安全，不要保存请求级状态。

### 6.5 与自定义参数解析器的配合

自定义参数解析器的绑定器每次调用都会收到当前登记表上的校验器：

```java
Object bindParameter(HttpContext ctx,
                     RequestParameter requestParameter,
                     BeanValidator beanValidator,
                     JsonSerializer jsonSerializer);
```

内置的 Bean 绑定器只在注解 `value = true` 时调用校验器。自定义绑定器如果绑定出 Bean 并需要校验，也应在返回前显式调用；是否校验、用哪组分组由绑定器自己决定。不需要校验时忽略这个参数即可，写法见[定制：参数解析器](./custom-parameter-parser.md)。

## 7. 编写约定

序列化器：

- 八个方法都要实现。服务器只用 `beanToJson`、`mapToBean`、`jsonToBean`，其余方法供应用代码与客户端使用，不能留空。
- `mapToBean` 要能处理 `String` 与 `List<String>` 两种值形态，这是 `@BeanQuery`、`@BeanForm` 的实际入参。
- 实现必须线程安全，实例全应用共享，不要保存请求级状态。
- 用运行时异常报错，优先包成 `SerializerException`；受检异常不要向外抛。
- 序列化器不处理字符集，返回的字符串由框架按缺省字符集编码。
- 未知字段策略由实现决定。默认 Jackson 实现忽略未知字段，`@BeanJson` 依赖这个行为；换成严格实现后，多传字段会直接失败。
- 流式响应的对象元素在订阅阶段序列化。此处抛异常只会把流置为错误信号，响应头可能已经发出，无法再改状态码。
- 在 `build()` 之前完成配置，构建后不要再修改序列化器实例。

校验器：

- 两个 `validate` 重载都要实现；分组为空时按默认分组执行，不要跳过校验。
- 违规时抛 `BeanValidationException`，并把全部违规交给它，不要逐条中断。
- 实现必须线程安全，每个带校验开关的 Bean 形参都会调用一次。
- 校验发生在调用控制器之前，状态码与响应体由异常处理器决定；不登记处理器时返回 500。
- 框架不调用 `close()`。持有引擎资源的实现需要在应用关闭时自行释放；默认实现自举的工厂由静态实例长期持有。
- 自定义参数解析器绑定 Bean 时，按需调用传入的 `beanValidator`，不要自行创建校验引擎。

[首页](../README.md) | [响应体系](./response.md) | [定制：响应写出策略](./custom-response-writing.md) | [定制：参数解析器](./custom-parameter-parser.md)
