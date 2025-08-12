package top.turboweb.commons.utils.base;

/**
 * 错误字符串生成器
 */
public class ErrorStrGenerator {

    /**
     * 错误页面
     * @param code 错误码
     * @param msg 错误信息
     * @return 错误页面
     */
    public static String errHtml(int code, String msg) {
        return """
                <h1>TurboWeb Error, Code: %d</h1>
                errMsg: %s
                """.formatted(code, msg);
    }
}
