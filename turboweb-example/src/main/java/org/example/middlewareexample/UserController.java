package org.example.middlewareexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.ViewModel;

@RequestPath("/user")
public class UserController {
	@Get
	public String example01(HttpContext c) {
		System.out.println("controller-user");
		return "example01";
	}

	@Get("/example02")
	public ViewModel example02(HttpContext c) {
		ViewModel model = new ViewModel();
		model.addAttribute("name", "turboweb");
		model.setViewName("index");
		return model;
	}

	@Get("/example06")
	public String example06(HttpContext c) throws InterruptedException {
		Thread.sleep(10000);
		return "example06";
	}

	@Get("/example07")
	public String example07(HttpContext c) throws InterruptedException {
		Thread.sleep(10000);
		return "example07";
	}
}
