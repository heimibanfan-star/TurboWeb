package org.example.template;

import top.turboweb.anno.Get;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.ViewModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
