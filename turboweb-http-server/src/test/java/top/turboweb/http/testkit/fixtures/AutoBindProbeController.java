package top.turboweb.http.testkit.fixtures;

import top.turboweb.rest.route.Get;

/**
 * 路由用例夹具：autoBind 模式下的零参路由。
 *
 * <p>零参是 autoBind 分支里唯一不依赖任何 parser 语义的形态（parser 语义归 Task 11），
 * 用来把"autoBind=true 走 {@code AutoBindRouterDefinition} 并能调通"钉成确定量，
 * 不与 `info/autobind` 包的正反用例重叠。
 */
public class AutoBindProbeController {

    @Get("/auto")
    public String auto() {
        return "auto";
    }
}
