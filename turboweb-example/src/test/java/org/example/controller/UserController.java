package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

	@Get("/{id}")
	public String helloUser(HttpContext c) {
		return "hello user";
	}
}
