# 监听器

在 TurboWeb 中，**监听器（Listener）机制用于感知服务器生命周期的关键阶段**，方便用户在特定节点插入初始化逻辑、资源预加载或自定义行为注册等操作。

监听器的定义与注册机制简洁灵活，是实现定制化启动流程的推荐方式。

**_核心特性_**

✅ 生命周期钩子清晰：支持在服务器初始化前和启动后两个关键阶段进行拦截与扩展。

✅ 执行顺序可控：用户注册的监听器按照添加顺序执行，框架内置监听器始终优先执行。

✅ 可配置启用/禁用内置监听器：提供灵活的开关机制，适配个性化场景。

## 基本使用

**_定义监听器_**

用户可通过实现 `TurboWebListener` 接口定义自定义监听器：

```java
public class MyListener implements TurboWebListener {
    @Override
    public void beforeServerInit() {
        System.out.println("在服务器进行初始化之间会触发");
    }

    @Override
    public void afterServerStart() {
        System.out.println("在服务器启动之后会触发");
    }
}
```

**_注册监听器_**

通过 `listeners()` 方法将自定义监听器注册到服务器引导类中：

```java
BootStrapTurboWebServer.create()
        .listeners(new MyListener())
        .start();
```

**_查看启动日志_**

```text
在服务器进行初始化之间会触发
13:44:57 [INFO ] [main] TurboWeb初始化前置监听器方法执行完成
...
在服务器启动之后会触发
13:44:57 [INFO ] [main] TurboWebServer start on: http://0.0.0.0:8080, time: 195ms
```

## 内置监听器

TurboWeb 提供了默认内置监听器 `DefaultJacksonTurboWebListener`，用于初始化全局的 `ObjectMapper` 实例，统一管理 JSON 序列化行为。

如需 **禁用默认监听器**，可使用如下配置：

```java
BootStrapTurboWebServer.create()
        .listeners(new MyListener())
        // 禁止执行默认的监听器
        .executeDefaultListener(false)
        .start();
```

> ⚠️ **建议保留默认监听器**，除非你需要完全接管 `ObjectMapper` 的配置与管理逻辑，否则禁用可能影响 JSON 序列化功能的默认行为。



[首页](../README.md) | [嵌入式网关](./gateway) | [服务器参数配置](./config.md)

