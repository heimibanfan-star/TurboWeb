package top.heimi.controllers;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get
	public Object hello(HttpContext ctx) {
		System.out.println("hello");
		return "hello world";
	}

	@Get("/set")
	public String set(HttpContext ctx) {
		ctx.getSession().setAttribute("name", "张三", 5000);
		return "success";
	}
}
