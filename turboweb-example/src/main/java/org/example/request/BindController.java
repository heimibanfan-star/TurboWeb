package org.example.request;


import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Post;
import top.turboweb.rest.router.Route;
import top.turboweb.rest.parameter.*;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;

import java.util.List;
import java.util.Set;

@Route("/bind")
public class BindController {

    @Get("/1")
    public String bind01(
            HttpContext ctx,
            HttpSession session,
            HttpCookies cookies,
            SseResponse sseResponse,
            SseEmitter sseEmitter
            ) {
        System.out.println(ctx);
        System.out.println(session);
        System.out.println(cookies);
        System.out.println(sseResponse);
        System.out.println(sseEmitter);
        return "success";
    }

    @Get("/2/{id:num}")
    public String bind02(@PathParam("id") Long id) {
        System.out.println(id);
        return "success";
    }

    @Get("/3")
    public String bind03(@QueryParam("id") Long id) {
        System.out.println(id);
        return "success";
    }

    @Get("/4")
    public String bind04(@QueryParam("ids") List<Long> ids) {
        System.out.println(ids);
        return "success";
    }

    @Get("/5")
    public String bind05(@QueryParam("ids") Set<Long> ids) {
        System.out.println(ids);
        return "success";
    }

    @Get("/6")
    public String bind06(@BeanQuery Student student) {
        System.out.println(student);
        return "success";
    }

    @Post("/7")
    public String bind07(@BeanForm Student student) {
        System.out.println(student);
        return "success";
    }

    @Post("/8")
    public String bind08(@BeanJson Student student) {
        System.out.println(student);
        return "success";
    }

    @Get("/9")
    public String bind09(@BeanQuery(value = true, groups = Groups.Add.class) Student student) {
        System.out.println(student);
        return "success";
    }
}
