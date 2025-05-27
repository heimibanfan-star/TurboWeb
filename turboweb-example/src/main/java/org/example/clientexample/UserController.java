package org.example.clientexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.Put;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {

	@Get("/example01")
	public String example01(HttpContext c) {
		User user = c.loadQuery(User.class);
		System.out.println(user);
		return "name:" + user.getName() + ",age:" + user.getAge();
	}

	@Get("/example02")
	public User example02(HttpContext c) {
		User user = c.loadQuery(User.class);
		System.out.println(user);
		return user;
	}

	@Post("/example03")
	public String example03(HttpContext c) {
		User user = c.loadForm(User.class);
		String authorization = c.getRequest().getHeaders().get("Authorization");
		System.out.println(authorization);
		System.out.println(user);
		return "name:" + user.getName() + ",age:" + user.getAge();
	}

	@Post("/example04")
	public String example04(HttpContext c) {
		User user = c.loadJson(User.class);
		System.out.println(user);
		return "name:" + user.getName() + ",age:" + user.getAge();
	}

	@Post("/example05")
	public String example05(HttpContext c) {
		User user = c.loadQuery(User.class);
		User user2 = c.loadJson(User.class);
		System.out.println(user);
		System.out.println(user2);
		return "name:" + user.getName() + ",age:" + user.getAge();
	}
}
