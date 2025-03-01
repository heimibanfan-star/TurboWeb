package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.response.ViewModel;
import org.turbo.web.core.http.session.Session;

import java.time.LocalDateTime;
import java.util.List;

@RequestPath("/user")
public class UserController {

    @Get
    public ViewModel index(HttpContext ctx) {
        ViewModel viewModel = new ViewModel();
        viewModel.setViewName("index");
        List<String> names = List.of("张三", "李四", "王五");
        viewModel.addAttribute("names", names);
        return viewModel;
    }

    @Get("/test")
    public String test(HttpContext ctx) {
        return "hello world";
    }
}
