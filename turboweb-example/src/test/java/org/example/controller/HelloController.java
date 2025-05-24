package org.example.controller;

import org.turboweb.commons.anno.Get;
import org.turboweb.commons.anno.RequestPath;
import org.turboweb.core.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/hello")
public class HelloController {

	@Get
	public String hello(HttpContext c) {
		return "hello world";
	}
}
