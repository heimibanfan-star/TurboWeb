/*
 * turboweb-http-runtime：HTTP 会话与 Cookie 的契约模块。
 * 六个接口（HttpSession、HttpSessionStore、SessionManager、SessionManagerHolder、
 * HttpCookie、HttpCookies）均为纯 JDK 依赖，不引第三方，也不依赖仓内其他模块；
 * 默认实现（Default*、Memory*、BackHole*）留在 turboweb-http-server。
 */

description = "turboweb-http-runtime"
