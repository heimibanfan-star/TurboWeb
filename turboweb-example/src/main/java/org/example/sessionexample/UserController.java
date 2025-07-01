package org.example.sessionexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.SseResponse;
import top.turboweb.http.session.HttpSession;

@RequestPath("/user")
public class UserController {
	@Get("/set")
	public String set(HttpContext c) {
		HttpSession httpSession = c.getHttpSession();
		httpSession.setAttr("name", "turboweb");
		return "set session";
	}

	@Get("/get")
	public String get(HttpContext c) {
		HttpSession session = c.getHttpSession();
		String name = (String) session.getAttr("name");
		return "session name: " + name;
	}

	@Get("/get2")
	public String get2(HttpContext c) {
		HttpSession session = c.getHttpSession();
		String name = session.getAttr("name", String.class);
		return "session name: " + name;
	}

	@Get("/setttl")
	public String setttl(HttpContext c) {
		HttpSession session = c.getHttpSession();
		session.setAttr("name", "turboweb", 10000);
		return "set session ttl";
	}

	@Get("/remove")
	public String remove(HttpContext c) {
		SseResponse sseResponse = c.createSseResponse();
		HttpSession session = c.getHttpSession();
		session.remAttr("name");
		return "remove session";
	}
}
