package org.example.responseexample;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.HttpInfoResponse;

import java.util.Map;

@RequestPath("/user")
public class UserController {

	@Get("/example01")
	public void example01(HttpContext c) {
		c.text("Hello TurboWeb");
	}

	@Get("/example02")
	public void example02(HttpContext c) {
		c.text(HttpResponseStatus.OK, "Hello TurboWeb");
	}

	@Get("/example03")
	public void example03(HttpContext c) {
		c.html("<h1>Hello TurboWeb</h1>");
	}

	@Get("/example04")
	public void example04(HttpContext c) {
		c.html(HttpResponseStatus.OK, "<h1>Hello TurboWeb</h1>");
	}

	@Get("/example05")
	public void example05(HttpContext c) {
		Map<String, String> userInfo = Map.of(
			"name", "TurboWeb",
			 "age", "18"
		);
		c.json(userInfo);
	}

	@Get("/example06")
	public void example06(HttpContext c) {
		Map<String, String> userInfo = Map.of(
			"name", "TurboWeb",
			"age", "18"
		);
		c.json(HttpResponseStatus.OK, userInfo);
	}

	@Get("/example07")
	public String example07(HttpContext c) {
		return "Hello TurboWeb";
	}

	@Get("/example08")
	public Map<?, ?> example08(HttpContext c) {
		return Map.of(
			"name", "TurboWeb",
			"age", "18"
		);
	}

	@Get("/example09")
	public HttpResponse example09(HttpContext c) {
		HttpInfoResponse response = new HttpInfoResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.setContent("<h1>Hello TurboWeb</h1>");
		response.setContentType("text/html");
		return response;
	}

	@Get("/example10")
	public String example10(HttpContext c) {
		c.text("Hello TurboWeb HttpContext");
		return "Hello TurboWeb Return";
	}
}
