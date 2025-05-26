package org.example.requestexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.commons.exception.TurboArgsValidationException;
import top.turboweb.http.context.HttpContext;

import java.util.List;

@RequestPath("/user")
public class UserController {

	@Get("/{id}")
	public String example01(HttpContext c) {
		String id = c.param("id");
		System.out.println(id);
		return "id:" + id;
	}

	@Get("/{id}/{name}/{age}/{sex}")
	public String example02(HttpContext c) {
		Long id = c.paramLong("id");
		String name = c.param("name");
		Integer age = c.paramInt("age");
		Boolean sex = c.paramBoolean("sex");
		System.out.println(id);
		System.out.println(name);
		System.out.println(age);
		System.out.println(sex);
		return "id:" + id + ",name:" + name + ",age:" + age + ",sex:" + sex;
	}

	@Get("/example03")
	public String example03(HttpContext c) {
		UserDTO userDTO = c.loadQuery(UserDTO.class);
		System.out.println(userDTO);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Post("/example04")
	public String example04(HttpContext c) {
		UserDTO userDTO = c.loadForm(UserDTO.class);
		System.out.println(userDTO);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Post("/example05")
	public String example05(HttpContext c) {
		UserDTO userDTO = c.loadJson(UserDTO.class);
		System.out.println(userDTO);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Get("/example06")
	public String example06(HttpContext c) {
		UserDTO userDTO = c.loadQuery(UserDTO.class);
		if (userDTO.getName() == null || userDTO.getName().isBlank()) {
			return "用户名不能为空";
		}
		if (userDTO.getAge() == null || userDTO.getAge() < 0 || userDTO.getAge() > 100) {
			return "年龄必须不为空，且在0-100之间";
		}
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Get("/example07")
	public String example07(HttpContext c) {
		UserDTO userDTO = c.loadQuery(UserDTO.class);
		c.validate(userDTO);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Get("/example08")
	public String example08(HttpContext c) {
		UserDTO userDTO = c.loadQuery(UserDTO.class);
		try {
			c.validate(userDTO);
		} catch (TurboArgsValidationException e) {
			List<String> errorMsg = e.getErrorMsg();
			for (String msg : errorMsg) {
				System.out.println(msg);
			}
		}
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}

	@Get("/example09")
	public String example09(HttpContext c) {
		UserDTO userDTO = c.loadValidQuery(UserDTO.class);
		return "name:" + userDTO.getName() + ",age:" + userDTO.getAge();
	}
}
