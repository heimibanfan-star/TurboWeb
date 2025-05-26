package org.example.quickstart;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/hello")
public class HelloController {
	@Get
	public String hello(HttpContext c) {
		return "Hello TurboWeb";
	}
}
