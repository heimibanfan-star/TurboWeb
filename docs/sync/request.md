# <img src="../image/logo.png"/> 

# 请求数据的处理

TurboWeb 框架采用 `HttpContext` 对象作为控制器方法的唯一参数，开发者可以通过 `HttpContext` 提供的 API 访问所有请求相关的数据。框架**不做参数预处理**，只有用户**主动调用方法时，才会进行解析和封装**，确保按需使用、轻量高效。

```java
@RequestPath("/user")
public class UserController {
}
```

下面的例子都在这个Controller接口中书写。

## 路径参数

```java
@Get("/{id}")
public String example01(HttpContext c) {
    String id = c.param("id");
    System.out.println(id);
    return "id:" + id;
}
```

发送如下的请求：

```http
GET http://localhost:8080/user/1
```

路径参数的定义需要在路径中使用 ``{}`` 来定义，中间的内容就是参数名。如上面的代码，参数名就是id。

对于路径参数的获取是通过 ``HttpContext`` 的 ``param()``方法来获取，参数要和路径参数的名字保持一致。

无论参数传递的是什么类型，TurboWeb都会以String的方式来接收。

如果可以确定用户传入的参数是某些类型，例如Long、Integer、Boolean等，TurboWeb提供了一系列参数封装的API可以自动进行类型的转换，如下面的代码：

```java
@Get("/{id}/{name}/{age}/{sex}")
public String example02(HttpContext c) { 
    Long id = c.paramLong("id");
    String name = c.param("name");
    Integer age = c.paramInt("age");
    Boolean sex = c.paramBoolean("sex");
    System.out.println(id);
    System.out.println(name);
    System.out.println(age);
    System.out.println(sex);
    return "id:" + id + ",name:" + name + ",age:" + age + ",sex:" + sex;
}
```

发送如下的请求：

```http
GET http://localhost:8080/user/1/TurboWeb/18/true
```

``paramLong`` 会将接收到的路径参数自动转化为Long类型。

``paramInt`` 会自动将接收到的路径参数转化为Integer类型。

``paramBoolean`` 会自动将接收到的路径参数转化为Boolean类型。

> 注意：
>
> 路径参数只能在控制器中获取，在中间件中是无法获取的，因为TurboWeb对路径参数的封装是在控制器中间件完成路由匹配之后才进行注入的，在请求经过控制器中间件时参数还没有被封装，会返回null。

## 查询参数

TurboWeb 提供了非常简洁的方式来获取查询参数，而无需依赖实体类。可以通过 `HttpContext` 的 `query(..)` 方法获取单个查询参数：

```java
@Get
public String simple01(HttpContext c) {
    String name = c.query("name");
    return "name:" + name;
}
```

假如请求的查询参数是 `?name=turboweb`，那么 `name` 的值就会被提取。如果该参数不存在，默认返回 `null`。

为了避免 `null` 值的出现，可以为查询参数设置**默认值**：

```java
@Get("/simple02")
public String simple02(HttpContext c) {
    String name = c.query("name", "turboweb");
    return "name:" + name;
}
```

如果客户端传递了相同的查询参数多次（例如：`?name=turbo&name=web`），TurboWeb 会将这些参数封装成一个集合：

```java
@Get("/simple02")
public String simple02(HttpContext c) {
    List<String> names = c.queries("name");
    return "name:" + names;
}
```

这里会将多个 `name` 参数封装到一个 `List` 中，如果没有该参数，则返回一个空的集合。

TurboWeb 还支持自动类型转换，可以直接将查询参数转换成 `Long`、`Integer`、`Boolean` 等类型。以下是一个例子：

```java
@Get("/simple04")
public String simple04(HttpContext c) {
    Integer age = c.queryInt("age");
    return "age:" + age;
}
```

当然，你也可以为这些类型指定默认值：

```java
@Get("/simple05")
public String simple05(HttpContext c) {
    Integer age = c.queryInt("age", 18);
    return "age:" + age;
}
```

对于多个参数的查询，同样可以使用复数方法：

```java
@Get("/simple06")
public String simple06(HttpContext c) {
    List<Integer> grades = c.queriesInt("grade");
    return "grade:" + grades;
}
```

> 注意：使用自动类型转换时，如果参数类型不匹配，会抛出异常。

此外，查询参数也可以通过实体类进行封装。首先，定义一个实体类：

```java
public class UserDTO {
	private String name;
	private Integer age;
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	public Integer getAge() {
		return age;
	}
	@Override
	public String toString() {
		return "UserDTO{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
```

> 注：
>
> 后面的Form参数以及JSON参数的封装都借助这个对象作为例子。

定义一个路由来接收路径参数：

```java
@Get("/example03")
public String example03(HttpContext c) {
    UserDTO userDTO = c.loadQuery(UserDTO.class);
    System.out.println(userDTO);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

发送请求：

```http
GET http://localhost:8080/user/example03?name=TurboWeb&age=18
```

查询参数的封装通过 ``HttpContext`` 的 ``loadQuery()`` 方法来进行封装，参数是实体类的字节码对象，TurboWeb会自动根据实体类的字节码对象创建该实体类对象，之后将查询参数封装到该对象中。

参数的名字要与实体类的属性名一致，如果名字不一致就无法封装到实体类中，只会将同名的属性进行封装。

## 表单参数

TurboWeb对于表单参数的封装页非常的简单，看如下的代码：

```java
@Post("/example04")
public String example04(HttpContext c) {
    UserDTO userDTO = c.loadForm(UserDTO.class);
    System.out.println(userDTO);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

发送请求：

```http
### example04
POST http://localhost:8080/user/example04
Content-Type: application/x-www-form-urlencoded

name=TurboWeb&age=18
```

表单参数的封装是通过 ``HttpContext`` 的 ``loadForm()`` 方法进行封装的，参数也是实体类的字节码对象。

和查询参数一样，也是需要保证参数名称和属性名一致。

> 注意：
>
> 在TurboWeb中，GET请求和DELETE请求由于没有请求体，因此GET和DELETE请求无法进行表单参数的封装。

## JSON参数

JSON参数可以说是在目前前后端分离的架构中最常使用的一种，对于TurboWeb来说，这种合适的参数封装也是轻而易举，看如下的代码：

```java
@Post("/example05")
public String example05(HttpContext c) {
    UserDTO userDTO = c.loadJson(UserDTO.class);
    System.out.println(userDTO);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

发送请求：

```http
POST http://localhost:8080/user/example05
Content-Type: application/json

{
  "name": "TurboWeb",
  "age": 18
}
```

JSON参数的封装是通过 ``HttpContext`` 的 ``loadJson()`` 方法进行封装的，参数也是实体类的字节码对象。

如查询参数和表单参数一样，也是需要保证参数名和属性名一致。

> 注意：
>
> 在TurboWeb中，GET请求和DELETE请求由于没有请求体，因此GET和DELETE请求无法进行JSON参数的封装。

## 数据校验

为了保证数据的合法性，对前端发送过来的数据进行校验往往来说是必不可少的异步，但是如果通过代码进行数据校验，那么往往是一件很繁琐的事情，请看如下的代码：

```java
@Get("/example06")
public String example06(HttpContext c) {
    UserDTO userDTO = c.loadQuery(UserDTO.class);
    if (userDTO.getName() == null || userDTO.getName().isBlank()) {
        return "用户名不能为空";
    }
    if (userDTO.getAge() == null || userDTO.getAge() < 0 || userDTO.getAge() > 100) {
        return "年龄必须不为空，且在0-100之间";
    }
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

发送请求：

```http
GET http://localhost:8080/user/example06?
```

可以看到上面的代码是如此的繁琐，打眼一看，这不不仅仅几行代码吗？但是 ``UserDTO`` 这个实体类可能在很多的控制器方法中使用，因此这样的代码需要写很多份，会显得特别的冗余。

那么应该怎么做呢？

这个问题TurboWeb也提供了解决方案，TurboWeb已经继承了数据校验的框架，并将其封装为简化的API。

这里就以查询参数为例，表单参数和JSON参数也是一样的：

1.在实体类上定义数据校验的规则

```java
package org.example.requestexample;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UserDTO {
	@NotBlank(message = "name can not be blank")
	private String name;
	@NotNull(message = "age can not be null")
	@Min(value = 0, message = "age can not less than 0")
	@Max(value = 100, message = "age can not greater than 100")
	private Integer age;
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	public Integer getAge() {
		return age;
	}
	@Override
	public String toString() {
		return "UserDTO{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
```

2.封装完成参数之后调用方法进行校验

```java
@Get("/example07")
public String example07(HttpContext c) {
    UserDTO userDTO = c.loadQuery(UserDTO.class);
    c.validate(userDTO);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

发送请求：

```http
GET http://localhost:8080/user/example07
```

响应结果如下：

```json
{
  "msg": "[age can not be null, name can not be blank]",
  "code": "500"
}
```

可以看到TurboWeb内置的数据校验起了作用。

如果数据校验不通过，TurboWeb会抛出一个 ``TurboArgsValidationException`` 异常对象，异常栈信息如下：

```text
exception.top.turboweb.commons.TurboArgsValidationException: [age can not be null, name can not be blank]
	at context.top.turboweb.http.FullHttpContext.validate(FullHttpContext.java:41)
	at org.example.requestexample.UserController.example07(UserController.java:67)
	at impl.dispatcher.router.top.turboweb.http.DefaultHttpDispatcher.dispatch(DefaultHttpDispatcher.java:55)
	at middleware.top.turboweb.http.HttpRouterDispatcherMiddleware.invoke(HttpRouterDispatcherMiddleware.java:19)
	at middleware.top.turboweb.http.Middleware.next(Middleware.java:35)
	at middleware.top.turboweb.http.SentinelMiddleware.invoke(SentinelMiddleware.java:16)
	at impl.scheduler.top.turboweb.http.VirtualThreadHttpScheduler.doExecute(VirtualThreadHttpScheduler.java:68)
	at impl.scheduler.top.turboweb.http.VirtualThreadHttpScheduler.lambda$execute$0(VirtualThreadHttpScheduler.java:46)
	at java.base/java.util.concurrent.ThreadPerTaskExecutor$TaskRunner.run(ThreadPerTaskExecutor.java:314)
	at java.base/java.lang.VirtualThread.run(VirtualThread.java:329)
```

可以通过该对象的 ``getErrorMsg()`` 方法获取数据校验中的异常信息，代码如下：

```java
try {
    c.validate(userDTO);
} catch (TurboArgsValidationException e) {
    List<String> errorMsg = e.getErrorMsg();
    for (String msg : errorMsg) {
        System.out.println(msg);
    }
}
```

这个时候又有开发者说了，我要进行数据校验，那么我还得在调用 ``HttpContext`` 的 ``validate()`` 方法进行校验，万一忘记了怎么办？不要着急，TurboWeb提供了一系列组合的API，可以在参数封装完成之后自动进行数据校验，如下：

这里我们还是使用查询参数为例子进行数据校验

```java
@Get("/example09")
public String example09(HttpContext c) {
    UserDTO userDTO = c.loadValidQuery(UserDTO.class);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

``HttpContext`` 的 ``loadValidQuery()`` 会自动在参数封装完成之后根据字段中配置的校验规则进行数据校验，原理就是内部调用了``loadQuery()`` 方法和 ``validate()`` 方法，参数也是实体类的字节码对，表单参数和JSON参数的校验方式也是这样，只不过API的名字不一样。

``loadValidQuery()`` 对查询参数进行封装并且进行数据校验。

``loadValidForm()`` 对表单参数进行封装并且进行数据校验。

``loadValidJson()`` 对JSON参数进行封装并且进行数据校验。

> 详细的校验规则请参考JSR303数据校验标准。

**分组校验**

TurboWeb对参数的封装也提供了分组校验，如下列的例子：

```java
@NotBlank(message = "用户名不能为空", groups = Groups.Add.class)
private String name;
@NotNull(message = "年龄不能为空", groups = Groups.Update.class)
private Integer age;
```

在这个例子中，`name` 字段的校验属于 `Add` 分组，而 `age` 字段的校验则属于 `Update` 分组。

分组校验使用示例：

```java
User user = c.loadValidQuery(User.class, Groups.Add.class);
```

由于 `loadValidQuery` 方法传入了 `Groups.Add.class`，所以此时只会校验 `name` 字段（用户名），而 `age` 字段（年龄）将不会进行校验。

```java
User user = c.loadValidQuery(User.class, Groups.Update.class);
```

当传入 `Groups.Update.class` 时，只有 `age` 字段（年龄）会被校验，而 `name` 字段（用户名）将不会被校验。

```java
User user = c.loadValidQuery(User.class, Groups.Update.class, Groups.Add.class);
```

如果同时传入 `Groups.Update.class` 和 `Groups.Add.class`，则会同时对 `name` 和 `age` 字段进行校验。

> 注意：
>
> 如果校验规则的注解没有指定分组，而校验方法传入了分组，那么该校验将被跳过，只有注解上指定分组的字段会进行校验。
>
> 这种分组校验机制可以帮助在不同场景下根据需要选择性地校验字段，提供灵活的校验策略。



[目录](./guide.md) [快速入门](./quickstart.md) 上一节 下一节 [响应数据的处理](./response.md)