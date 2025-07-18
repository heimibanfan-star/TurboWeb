# 模板技术的支持

TurboWeb模板技术的实现也是基于中间件，模板实现的中间件继承了 `TemplateMiddleware` ，TurboWeb中提供的默认实现是基于Freemarker的模板。

## 基本使用

创建模板文件：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>${title}</title>
</head>
<body>
<h1>欢迎 ${username}!</h1>
<p>邮箱: ${email}</p>

<#if articles?? && (articles?size &gt; 0)>
    <h2>文章列表</h2>
    <ul>
        <#list articles as article>
            <li>${article.title} (${article.date})</li>
        </#list>
    </ul>
</#if>
</body>
</html>
```

创建Controller接口：

```java
@RequestPath("/user")
public class UserController {

    @Get("/profile")
    public ViewModel showProfile(HttpContext context) {
        ViewModel model = new ViewModel();
        model.setViewName("user"); // 指定模板名（无需后缀）
        model.addAttribute("title", "用户中心");
        model.addAttribute("username", "TurboWeb");
        model.addAttribute("email", "user@example.com");

        // 添加复杂数据（如列表）
        List<Map<String, Object>> articles = new ArrayList<>();
        articles.add(Map.of("title", "TurboWeb教程", "date", LocalDate.now()));
        articles.add(Map.of("title", "Java高性能编程", "date", LocalDate.now().minusDays(3)));
        model.addAttribute("articles", articles);

        return model;
    }
}
```

注册Controller和模板中间件：

```java
AnnoRouterManager routerManager = new AnnoRouterManager();
routerManager.addController(new UserController());
// 创建模板中间件
TemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();
BootStrapTurboWebServer.create()
        .http()
        .middleware(templateMiddleware)
        .routerManager(routerManager)
        .and().start();
```

当注册了模板中间件之后，Controller接口返回 `ViewModel`就会被进行模板渲染。

`addAttribute(..)` 是添加属性。

`setViewName(..) ` 是设置模板的文件名。

## 高级配置

```java
FreemarkerTemplateMiddleware templateMiddleware = new FreemarkerTemplateMiddleware();

// 自定义模板路径（默认：templates）
templateMiddleware.setTemplatePath("views");

// 自定义模板后缀（默认：.ftl）
templateMiddleware.setTemplateSuffix(".html");

// 启用模板缓存（生产环境推荐）
templateMiddleware.setOpenCache(true);
```



[首页](../README.md) | [静态资源的支持](./staticresource.md) | [运行信息的获取](./serverinfo.md)

