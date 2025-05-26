package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get
	public String hello(HttpContext c) {
		return (String) c.getSession().getAttribute("name");
	}

	@Get("/set")
	public String set(HttpContext c) {
		c.getSession().setAttribute("name", "张三", 10000);
		return "set session";
	}
}
