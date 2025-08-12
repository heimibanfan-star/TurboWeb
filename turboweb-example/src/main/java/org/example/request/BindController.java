package org.example.request;

import top.turboweb.commons.anno.*;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.http.response.SseEmitter;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

import java.util.List;
import java.util.Set;

@RequestPath("/bind")
public class BindController {

    @Get("/1")
    public String bind01(
            HttpContext ctx,
            HttpSession session,
            HttpCookieManager cookieManager,
            SseResponse sseResponse,
            SseEmitter sseEmitter
            ) {
        System.out.println(ctx);
        System.out.println(session);
        System.out.println(cookieManager);
        System.out.println(sseResponse);
        System.out.println(sseEmitter);
        return "success";
    }

    @Get("/2/{id:num}")
    public String bind02(@Param("id") Long id) {
        System.out.println(id);
        return "success";
    }

    @Get("/3")
    public String bind03(@Query("id") Long id) {
        System.out.println(id);
        return "success";
    }

    @Get("/4")
    public String bind04(@Query("ids") List<Long> ids) {
        System.out.println(ids);
        return "success";
    }

    @Get("/5")
    public String bind05(@Query("ids") Set<Long> ids) {
        System.out.println(ids);
        return "success";
    }

    @Get("/6")
    public String bind06(@QueryModel Student student) {
        System.out.println(student);
        return "success";
    }

    @Post("/7")
    public String bind07(@FormModel Student student) {
        System.out.println(student);
        return "success";
    }

    @Post("/8")
    public String bind08(@JsonModel Student student) {
        System.out.println(student);
        return "success";
    }

    @Get("/9")
    public String bind09(@QueryModel(value = true, groups = Groups.Add.class) Student student) {
        System.out.println(student);
        return "success";
    }
}
