package top.turboweb.http.orchestrator;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 异常响应文本生成器：集中定义异常兜底页的 HTML 模板，产出可直接作为响应正文的页面文本。
 * <p>
 * 纯函数，无日志、无副作用；页面嵌入的外来文本（异常消息、栈文本）一律经 HTML 实体转义，
 * 其中的标记字符不会作为原始标签透传给浏览器。
 * </p>
 */
public class ExceptionMessageHelper {

    private ExceptionMessageHelper() {
    }

    /**
     * 生成路由未匹配的 404 错误页面文本：展示异常消息，消息为 null 时回落异常类名。
     * 该情况不是服务器故障，页面不携带异常栈。
     *
     * @param throwable 触发 404 的异常
     * @return 完整的 HTML 页面文本
     */
    public static String notFoundMessage(Throwable throwable) {
        String message = throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage();
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>TurboWeb Error</title>
                    <style>
                        body { font-family: Consolas, "Courier New", monospace; margin: 2em; }
                        h1 { color: #c0392b; }
                        .error-message { font-size: 1.1em; }
                    </style>
                </head>
                <body>
                    <h1>404 Not Found</h1>
                    <p class="error-message">Exception message: %s</p>
                </body>
                </html>
                """.formatted(htmlEscape(message));
    }

    /**
     * 生成服务器异常的 500 错误页面文本：展示异常类型与完整异常栈，
     * 栈文本（含 Caused by 链）经 HTML 转义后嵌入可横向滚动的 pre 区块。
     *
     * @param throwable 触发兜底的原始异常
     * @return 完整的 HTML 页面文本
     */
    public static String errorMessage(Throwable throwable) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>TurboWeb Error</title>
                    <style>
                        body { font-family: Consolas, "Courier New", monospace; margin: 2em; }
                        h1 { color: #c0392b; }
                        .error-type { font-size: 1.1em; }
                        pre { background-color: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 6px; padding: 1em; overflow-x: auto; }
                    </style>
                </head>
                <body>
                    <h1>500 Internal Server Error</h1>
                    <p class="error-type">Exception type: %s</p>
                    <pre>%s</pre>
                </body>
                </html>
                """.formatted(throwable.getClass().getName(), htmlEscape(writer.toString()));
    }

    /**
     * 对文本做 HTML 实体转义，使异常栈等外来文本可安全嵌入页面正文。
     *
     * @param text 待转义文本
     * @return 转义后的文本
     */
    private static String htmlEscape(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }
}
