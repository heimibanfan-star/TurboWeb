package org.example.routerexample;

import org.turboweb.commons.anno.*;
import org.turboweb.http.context.HttpContext;

@RequestPath("/hello")
public class UserController {

	@Get("/{id}")
	public String example01(HttpContext c) {
		return "example01";
	}

	@Get("/example02/{id}")
	public String example02(HttpContext c) {
		return "example02";
	}

	@Get("/example02/10")
	public String example0210(HttpContext c) {
		return "example02 10";
	}

	@Get
	public String example03(HttpContext c) {
		return "example03 GET";
	}

	@Post
	public String example04(HttpContext c) {
		return "example04 POST";
	}

	@Patch
	public String example05(HttpContext c) {
		return "example05 PATCH";
	}

	@Put
	public String example06(HttpContext c) {
		return "example06 PUT";
	}

	@Delete
	public String example07(HttpContext c) {
		return "example07 DELETE";
	}

	@Get("/err/{id}/{name}")
	public String example09(HttpContext c) {
		return "example09";
	}

	@Get("/err/{name}/{id}")
	public String example10(HttpContext c) {
		return "example10";
	}
}
