package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

/**
 * TODO
 */
@RequestPath("/order")
public class OrderController {

	@Get
	public String helloOrder(HttpContext c) {
		return "hello order";
	}
}
