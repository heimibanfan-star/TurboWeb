package org.example.controller;

import org.turboweb.commons.anno.Get;
import org.turboweb.commons.anno.RequestPath;
import org.turboweb.http.context.HttpContext;

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
