package org.example.sessionexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.session.Session;

@RequestPath("/user")
public class UserController {
	@Get("/set")
	public String set(HttpContext c) {
		Session session = c.getSession();
		session.setAttribute("name", "turboweb");
		return "set session";
	}

	@Get("/get")
	public String get(HttpContext c) {
		Session session = c.getSession();
		String name = (String) session.getAttribute("name");
		return "session name: " + name;
	}

	@Get("/setttl")
	public String setttl(HttpContext c) {
		Session session = c.getSession();
		session.setAttribute("name", "turboweb", 10000);
		return "set session ttl";
	}

	@Get("/remove")
	public String remove(HttpContext c) {
		Session session = c.getSession();
		session.removeAttribute("name");
		return "remove session";
	}
}
