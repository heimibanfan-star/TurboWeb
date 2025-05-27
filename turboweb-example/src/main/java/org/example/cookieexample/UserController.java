package org.example.cookieexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.cookie.HttpCookie;

@RequestPath("/user")
public class UserController {
	@Get("/example01")
	public String example01(HttpContext c) {
		HttpCookie httpCookie = c.getHttpCookie();
		httpCookie.setCookie("name", "turbo");
		return "example01";
	}

	@Get("/example02")
	public String example02(HttpContext c) {
		HttpCookie httpCookie = c.getHttpCookie();
		String name = httpCookie.getCookie("name");
		return "example02:" + name;
	}
}
