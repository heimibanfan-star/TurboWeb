package org.example.nodeshare;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
	@Get
	public String user(HttpContext c) {
		return "user service";
	}
}
