package org.example.controller;

import org.turboweb.commons.anno.Get;
import org.turboweb.commons.anno.RequestPath;
import org.turboweb.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

	@Get
	public String helloUser(HttpContext c) {
		return "hello user";
	}
}
