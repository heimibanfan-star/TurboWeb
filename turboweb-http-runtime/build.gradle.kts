/*
 * turboweb-http-runtime：HTTP 侧的契约模块。
 * 契约接口（会话、Cookie、连接、上下文与挂载）不引第三方依赖；
 * 上下文契约经 api 暴露对 turboweb-rest-core 的依赖（HttpContext 的签名引用 HttpRequestMessage），
 * 其余接口均为纯 JDK 依赖；实现类（Default*、Memory*、BackHole*、FullHttpContext）留在 turboweb-http-server。
 */

description = "turboweb-http-runtime"

dependencies {
    // HttpContext 的公开签名引用 rest-core 的 HttpRequestMessage，须以 api 传递
    api(project(":turboweb-rest-core"))
}
