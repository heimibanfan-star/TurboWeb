# <img src="../image/logo.png"/>

# 异常处理器

TurboWeb 提供统一的异常处理机制，支持全局异常捕获与定制化响应输出，帮助开发者优雅地处理运行时抛出的异常，避免异常信息直接暴露给前端用户，从而提升系统的健壮性、安全性与可维护性。

异常处理器在 TurboWeb 中的使用非常简单，以下是一个最小可运行的示例。

定义一个路由方法，并且抛出一个异常：

```java
@RequestPath("/user")
public class UserController {
	@Get
	public void example01(HttpContext c) {
		throw new RuntimeException("example01");
	}
}
```

该控制器的 `/user` 路由在被访问时会抛出一个 `RuntimeException`。

创建全局异常处理器来捕获异常：

```java
public class GlobalExceptionHandler {
	@ExceptionHandler(RuntimeException.class)
	public Map<String, String> doRuntimeException(RuntimeException e) {
		return Map.of("message", "runtimeException");
	}
}
```

该方法将在遇到 `RuntimeException` 时被调用，并返回 `Map` 作为响应。

在服务器实例上注册异常处理器和控制器：

```java
public class ExceptionHandlerApplication {
	public static void main(String[] args) {
		TurboWebServer server = new StandardTurboWebServer(ExceptionHandlerApplication.class);
		server.controllers(new UserController());
		server.exceptionHandlers(new GlobalExceptionHandler());
		server.start();
	}
}
```

访问接口：

```http
GET http://localhost:8080/user
```

之后就可以看到响应内容如下：

```json
{
  "message": "runtimeException"
}
```

说明异常已被 `GlobalExceptionHandler` 捕获并正确处理。

``@ExceptionHandler(...)`` 标注该方法是异常处理的方法，参数是捕获的异常类型。

有的时候可能在捕获到不同的异常之后返回不同的响应状态码，可以使用``@ExceptionResponseStatus(...)`` 参数是状态码，如下代码：

```java
@ExceptionHandler(RuntimeException.class)
@ExceptionResponseStatus(500)
public Map<String, String> doRuntimeException(RuntimeException e) {
    return Map.of("message", "runtimeException");
}
```

在这里，当调度器捕获到 ``RuntimeException`` 的时候会返回字符串内容并且返回状态码为500。

> 注意：
>
> 异常处理器返回的内容会被自动序列化，并以 `application/json` 格式响应给客户端，等同于对返回值的弱化处理。
>
> 目前异常处理器不支持直接返回 `HttpResponse` 类型的响应。如果返回了该类型，TurboWeb 会主动抛出异常，并交由默认的异常处理器进行处理。
>
> 原因在于，异常处理器设计的初衷是对异常信息进行统一封装和格式化输出，自动序列化为 JSON 格式以保证响应的一致性和简洁性。如果允许直接返回 `HttpResponse`，则会绕过这一流程，导致响应格式不统一，破坏异常处理的规范化管理，也可能引发额外的处理逻辑冲突和难以预料的错误。

## 工作机制

由于 TurboWeb 采用 **中间件驱动** 的架构进行请求处理，因此，所有在中间件链路中抛出的异常都将被调度器捕获，并交由异常适配器统一处理。开发者无需担心异常是否发生在控制器内部或其他中间件中，系统都能实现统一捕获与响应。

所有通过 `@ExceptionHandler` 注解标注的方法都会被识别为异常处理器方法，并根据异常类型进行匹配。TurboWeb 的异常匹配机制支持类的继承关系：当一个异常对象匹配多个处理方法时，系统会优先调用**与该异常类型最接近**的处理方法，确保响应的准确性与上下文一致性。

此外，TurboWeb 内置了一个默认异常处理器，作为**最低优先级**的兜底处理方案。只有当开发者注册的所有异常处理器均无法匹配当前异常时，才会由内置异常处理器接管并生成默认响应，避免异常泄露并保证系统稳定运行。

## 异常处理器配合数据校验

定义一个实体类，配置数据校验的规则：

```java
public class UserDTO {
	@NotBlank(message = "name can not be blank")
	private String name;
	@NotNull(message = "password can not be blank")
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
}
```

在路由函数中对请求数据进行校验：

```java
@Post("/save")
public String save(HttpContext c) {
    UserDTO userDTO = c.loadValidJson(UserDTO.class);
    return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
}
```

编写异常处理器方法，处理数据校验异常：

```java
@ExceptionHandler(TurboArgsValidationException.class)
public Map<String, String> doTurboArgsValidationException(TurboArgsValidationException e) {
    StringBuilder errMsg = new StringBuilder();
    for (String s : e.getErrorMsg()) {
        errMsg.append(s).append(";");
    }
    return Map.of("message", errMsg.toString());
}
```

发送一个空的json，测试数据校验：

```http
POST http://localhost:8080/user/save
Content-Type: application/json

{

}
```

可以看到了响应内容如下：

```json
{
  "message": "name can not be blank;password can not be blank;"
}
```

这个时候数据校验的提示信息已经成功返回给客户端了。



[目录](./guide.md) [文件的处理](./file.md) 上一节 下一节 [中间件的使用](./middleware.md)