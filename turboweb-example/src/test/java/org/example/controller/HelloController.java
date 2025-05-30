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
		return (String) c.getHttpSession().getAttr("name");
	}

	@Get("/set")
	public String set(HttpContext c) {
		c.getHttpSession().setAttr("name", "张三", 10000);
		return "set session";
	}
}
