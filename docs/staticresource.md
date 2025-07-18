# 静态资源的支持

TurboWeb 通过 `StaticResourceMiddleware` 中间件提供静态资源服务，支持从类路径 `resources` 目录加载静态文件。未来计划扩展对磁盘路径的支持。

## 基本使用

```java
StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();
BootStrapTurboWebServer.create()
        .http().middleware(staticResourceMiddleware)
        .and().start();
```

拦截所有以 `/static` 开头的请求（如 `http://localhost:8080/static/js/main.js`）

从类路径 `resources/static` 目录查找对应文件

自动处理常见静态资源类型（HTML/CSS/JS/ 图片等）

## 高级配置选项

该中间件也提供了一系列可配置的属性：

```java
StaticResourceMiddleware staticResourceMiddleware = new StaticResourceMiddleware();

// 启用静态资源缓存（生产环境推荐）
staticResourceMiddleware.setCacheStaticResource(true);

// 设置缓存文件大小上限（单位：字节）
staticResourceMiddleware.setCacheFileSize(1024 * 1024); // 1MB

// 自定义静态资源请求路径前缀
staticResourceMiddleware.setStaticResourceUri("/assets");

// 自定义类路径下的静态资源目录
staticResourceMiddleware.setStaticResourcePath("/public");

// 设置缓存时间（单位：秒）
staticResourceMiddleware.setCacheControlMaxAge(3600);

// 注册额外的MIME类型映射
staticResourceMiddleware.addMimeType("wasm", "application/wasm");
```



[首页](./README.md) | [拦截器的使用](./interceptor.md) | [模板技术的支持](./template.md)
