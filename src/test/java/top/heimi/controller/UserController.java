package top.heimi.controller;

import org.turbo.web.anno.Get;
import org.turbo.web.anno.Post;
import org.turbo.web.anno.RequestPath;
import org.turbo.web.core.http.context.HttpContext;
import top.heimi.pojos.User;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

	@Post
	public void testGet(HttpContext ctx) {
		User getUser = ctx.loadQuery(User.class);
		User user = ctx.loadValidJson(User.class);
		System.out.println("get" + getUser);
		System.out.println("post" + user);
		ctx.text("testPost");
	}
}
