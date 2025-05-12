package top.heimi.controllers;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.Patch;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get("/{id}/{age}/{sex}")
	public Object hello(HttpContext ctx) {
		Long id = ctx.paramLong("id");
		Integer age = ctx.paramInt("age");
		Boolean sex = ctx.paramBoolean("sex");
		System.out.println(id);
		System.out.println(age);
		System.out.println(sex);
		return "hello world";
	}

	@Patch("/{id}")
	public Object hello2(HttpContext ctx) {
		Long id = ctx.paramLong("id");
		System.out.println(id);
		return "hello world";
	}
}
