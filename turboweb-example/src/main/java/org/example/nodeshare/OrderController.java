package org.example.nodeshare;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/order")
public class OrderController {
	@Get
	public String order(HttpContext c) {
		return "order service";
	}
}
