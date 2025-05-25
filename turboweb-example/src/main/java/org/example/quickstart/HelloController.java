package org.example.quickstart;

import org.turboweb.commons.anno.Get;
import org.turboweb.commons.anno.RequestPath;
import org.turboweb.http.context.HttpContext;

@RequestPath("/hello")
public class HelloController {
	@Get
	public String hello(HttpContext c) {
		return "Hello TurboWeb";
	}
}
