package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import reactor.core.publisher.Mono;

/**
 * TODO
 */
@RequestPath("/user")
public class ReactiveUserController {

	@Get
	public Mono<String> hello(HttpContext ctx) {
		return Mono.just("hello world");
	}
}
