package org.example.exceptionhandler;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/user")
public class UserController {
	@Get
	public void example01(HttpContext c) {
		throw new RuntimeException("example01");
	}

	@Post("/save")
	public String save(HttpContext c) {
		UserDTO userDTO = c.loadValidJson(UserDTO.class);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}
}
