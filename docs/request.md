# 请求数据的处理

## 提取URL中的参数

在 TurboWeb 中，客户端通过 URL 请求参数传递的数据，可以通过 `HttpContext` 提供的系列方法进行提取。TurboWeb 支持单值提取、多值提取、类型自动转换以及默认值设置。

### 单值参数提取

示例：

```java
@Get("/user01")
public String user01(HttpContext context) {
    String name = context.query("name");
    return "name=" + name;
}
```

访问：

```http
GET http://localhost:8080/user01?name=张三
```

**_带默认值提取_**

```java
String name = context.query("name", "TurboWeb");
```

当请求中没有对应参数时，可以通过提供默认值避免返回 null：

### 多值参数提取（同名参数）

示例：

```java
@Get("/user02")
public String user02(HttpContext context) {
    List<String> names = context.queries("name");
    return "name:" + names;
}
```

访问：

```http
GET http://localhost:8080/user02?name=张三&name=李四
```

### 自动类型转换

当你确定参数是整数、长整型或布尔类型时，TurboWeb 提供了便捷的类型转换方法。如果参数格式不合法，会抛出相应的转换异常（如 `NumberFormatException`）。

示例：整数参数

```java
@Get("/user03")
public String user03(HttpContext context) {
    Integer age = context.queryInt("age");
    return "age:" + age;
}
```

访问：

```http
GET http://localhost:8080/user03?age=18
```

### 参数提取方法汇总

| 方法                                                   | 说明                                         |
| ------------------------------------------------------ | -------------------------------------------- |
| `String query(String name)`                            | 获取指定参数名的值，返回第一个匹配项         |
| `String query(String name, String defaultValue)`       | 获取参数值，如果未提供则返回默认值           |
| `List<String> queries(String name)`                    | 获取所有同名参数，返回字符串列表             |
| `Integer queryInt(String name)`                        | 获取参数并转换为 Integer                     |
| `Integer queryInt(String name, int defaultValue)`      | 获取参数并转换为 Integer，不存在则返回默认值 |
| `List<Integer> queriesInt(String name)`                | 获取所有同名参数并转换为 Integer 列表        |
| `Long queryLong(String name)`                          | 获取参数并转换为 Long                        |
| `Long queryLong(String name, long defaultValue)`       | 同上，带默认值                               |
| `List<Long> queriesLong(String name)`                  | 获取并转换所有 Long 类型参数                 |
| `Boolean queryBool(String name)`                       | 获取参数并转换为 Boolean                     |
| `Boolean queryBool(String name, Boolean defaultValue)` | 同上，带默认值                               |
| `List<Boolean> queriesBool(String name)`               | 获取所有参数并转换为 Boolean 列表            |

## 请求参数与Java实体对象的映射

当 URL 参数较多时，如果逐个使用 `query(..)` 方法进行提取，代码会显得繁琐。为此，**TurboWeb 提供了一系列以 `load` 开头的方法**，用于将 URL 参数、表单参数、JSON 请求体等内容自动绑定为 Java 实体对象。

### 将URL参数映射为实体对象

假设我们要实现一个分页接口，URL 参数为：

```http
GET http://localhost:8080/user04?pageNum=2&pageSize=20
```

我们可以定义一个分页实体类：

```java
public class PageDTO {
    private int pageNum;
    private int pageSize;
    
	// Getter / Setter / toString 省略
}
```

然后在接口中使用 `loadQuery(..)` 自动将请求参数映射为实体对象：

```java
@Get("/user04")
public String user04(HttpContext context) {
    PageDTO pageDTO = context.loadQuery(PageDTO.class);
    return "pageNum:" + pageDTO.getPageNum() + " pageSize:" + pageDTO.getPageSize();
}
```

只要参数名与实体属性名一致，TurboWeb 就会自动完成映射。

若某些参数为可选项，可以直接在实体类中为字段设置默认值：

```java
public class PageDTO {
    private int pageNum = 1;
    private int pageSize = 10;

    ...
}
```

当 URL 中不提供 `pageNum` 或 `pageSize` 时，就会使用实体中的默认值。

### 表单参数映射为实体对象

对于 `application/x-www-form-urlencoded` 类型的请求（常见于 HTML 表单提交），TurboWeb 提供了 `loadForm(..)` 方法，可将表单数据直接映射为 Java 实体对象。

定义一个用户实体类：

```java
public class UserDTO {
    private String username;
    private String nickname;
    private String password;

    // Getter / Setter / toString 省略
}
```

处理表单提交：

```java
@Post("/user05")
public String user05(HttpContext context) {
    UserDTO userDTO = context.loadForm(UserDTO.class);
    System.out.println(userDTO);
    return "user05";
}
```

请求示例：

```http
POST http://localhost:8080/user05
Content-Type: application/x-www-form-urlencoded

username=zhangsan&nickname=张三&password=123456
```

和 URL 参数一样，实体类字段也可设置默认值：

```java
private String nickname = "默认昵称";
```

### JSON 请求体映射为实体对象

对于 `application/json` 类型的请求，TurboWeb 提供 `loadJson(..)` 方法，可以将 JSON 请求体直接反序列化为 Java 对象。

复用 `UserDTO` 实体类，处理 JSON 请求：

```java
@Post("/user06")
public String user06(HttpContext context) {
    UserDTO userDTO = context.loadJson(UserDTO.class);
    System.out.println(userDTO);
    return "user06";
}
```

请求示例：

```http
POST http://localhost:8080/user06
Content-Type: application/json

{
  "username": "lisi",
  "nickname": "李四",
  "password": "123456"
}
```

与前面方式一致，在实体类中直接给字段赋值即可：

```java
private String password = "默认密码";
```

### 参数的有效性校验

TurboWeb 支持基于 **JSR-303（Jakarta Bean Validation）** 的参数校验机制，可配合 `loadXXX` 系列方法对 URL 参数、表单参数和 JSON 请求体进行校验。

在实体类中添加校验注解：

以 `UserDTO` 为例，我们通过添加 JSR-303 注解实现字段的非空校验：

```java
public class UserDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    private String password;

    // Getter / Setter / toString 略
}
```

在接口中执行校验：

使用 `context.validate(..)` 手动校验已封装的对象：

```java
@Post("/user07")
public String user07(HttpContext context) {
    UserDTO userDTO = context.loadJson(UserDTO.class);
    context.validate(userDTO); // 触发校验
    return "user07";
}
```

当字段校验不通过时，将抛出 `TurboArgsValidationException`，可以捕获该异常并提取具体错误信息：

```java
try {
    context.validate(userDTO);
} catch (TurboArgsValidationException e) {
    List<String> errorMsg = e.getErrorMsg();
    for (String msg : errorMsg) {
        System.out.println(msg);
    }
}
```

**_一步封装 + 校验：loadValidXXX_**

为了简化封装与校验的调用，TurboWeb 提供了快捷方法：

| 方法                       | 说明                           |
| -------------------------- | ------------------------------ |
| `loadValidQuery(Class<T>)` | 从 URL 参数中封装并校验实体    |
| `loadValidForm(Class<T>)`  | 从表单数据中封装并校验实体     |
| `loadValidJson(Class<T>)`  | 从 JSON 请求体中封装并校验实体 |

示例：

```java
UserDTO userDTO = context.loadValidJson(UserDTO.class);
```

等价于：

```java
UserDTO userDTO = context.loadJson(UserDTO.class);
context.validate(userDTO);
```

**_分组校验的支持_**

TurboWeb 支持 JSR-303 的 **分组校验** 功能，可满足新增、修改等不同场景下的校验要求。

定义校验分组：

```java
public interface Groups {
    interface Add {}
    interface Update {}
}
```

实体字段中指定校验分组：

```java
public class Student {

    @NotNull(message = "id不能为空", groups = Groups.Update.class)
    private Long id;

    @NotBlank(message = "name不能为空", groups = {Groups.Add.class, Groups.Update.class})
    private String name;

    // Getter / Setter / toString 略
}
```

执行指定分组的校验：

```java
// 新增操作（不校验 id）
Student student = context.loadValidJson(Student.class, Groups.Add.class);

// 更新操作（必须传 id）
Student student = context.loadValidJson(Student.class, Groups.Update.class);
```

## REST风格的路径占位符

除了 URL 查询参数、表单参数和 JSON 参数，TurboWeb 还支持 **REST 风格的路径占位符**，可以直接从请求路径中提取参数。

**_基本用法_**

定义路径时，可以在路由中使用 `{paramName}` 作为占位符：

```java
@Get("/user09/{name}")
public String user09(HttpContext context) {
    String name = context.param("name");
    return "name=" + name;
}
```

请求示例：

```http
GET http://localhost:8080/user09/zhangsan
```

这里的 `param("name")` 会提取路径中的 `zhangsan`。

**_路径占位符的类型声明_**

默认情况下，路径参数类型为字符串（`str`），也可以显式声明类型：

```java
@Get("/user09/{name:str}")
public String user09(HttpContext context) {
    String name = context.param("name");
    return "name=" + name;
}
```

TurboWeb 支持以下内置类型：

| 类型   | 说明                                 |
| ------ | ------------------------------------ |
| `str`  | 字符串（默认类型）                   |
| `num`  | 数字，匹配整数和浮点数（正负号可选） |
| `int`  | 整数（不带小数）                     |
| `bool` | 布尔值，支持 `true` / `false`        |
| `date` | 日期，格式为 `yyyy-MM-dd`            |
| `ipv4` | IPv4 地址格式                        |

示例：

```java
@Get("/user/{id:int}")
public String getUserById(HttpContext context) {
    Integer id = context.paramInt("id");
    return "userId=" + id;
}
```

**_支持正则表达式定义路径参数_**

如果内置类型无法满足需求，可以通过正则表达式自定义匹配规则：

```java
@Get("/order/{orderId:regex=\\d{4}-[A-Z]{3}}")
public String getOrder(HttpContext context) {
    String orderId = context.param("orderId");
    return "orderId=" + orderId;
}
```

**_路径参数的自动类型转换_**

类似于 `queryInt()` 等方法，TurboWeb 提供了多种自动转换方法方便直接获取对应类型的路径参数：

| 方法                       | 说明                                         |
| -------------------------- | -------------------------------------------- |
| `param(String name)`       | 返回字符串类型参数                           |
| `paramInt(String name)`    | 返回整型参数                                 |
| `paramLong(String name)`   | 返回长整型参数                               |
| `paramBool(String name)`   | 返回布尔类型参数                             |
| `paramDouble(String name)` | 返回浮点类型参数                             |
| `paramDate(String name)`   | 返回 `LocalDate` 类型参数，格式 `yyyy-MM-dd` |

**_注意事项_**

路径参数名称必须与 `param(..)` 中使用的名称一致。

路径参数自动转换如格式不正确，会抛出转换异常。



[首页](../README.md) | [路由体系](./router.md) | [响应数据的处理]()