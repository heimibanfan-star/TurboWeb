package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.http.core.StatusLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * 异常响应文本生成器：以统一的三段式 HTML 模板生成报错页，
 * 页面依次呈现状态码、首部信息与堆栈三个部分，产出可直接作为响应正文的页面文本。
 * <p>
 * 纯函数，无日志、无副作用；页面嵌入的外来文本（首部信息、异常消息、栈文本）
 * 一律经 HTML 实体转义，其中的标记字符不会作为原始标签透传给浏览器。
 * </p>
 */
public class ExceptionMessageHelper {

    /**
     * 通用报错页模板：三段式结构，依次为 h1 状态码、p 首部信息与可选的 pre 堆栈区块，
     * 堆栈占位为空字符串时不渲染堆栈区块。
     */
    private static final String ERROR_PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>TurboWeb Error</title>
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
            
                    body {
                        font-family: Consolas, "Courier New", monospace;
                        background-color: #f6f8fa;
                        min-height: 100vh;
                        padding: 2.5rem 0;
                        line-height: 1.6;
                        color: #24292f;
                    }

                    /* 宽度按视口占满，无圆角无边框 */
                    .error-page {
                        width: 100vw;
                        margin: 0 auto;
                        background-color: #ffffff;
                        padding: 2rem 2.5rem;
                        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }
            
                    h1 {
                        color: #0550ae;              /* 蓝色主标题 */
                        font-size: 1.9rem;
                        font-weight: 600;
                        margin: 0 0 1.2rem 0;
                        padding-bottom: 0.6rem;
                        border-bottom: 1px solid #d0d7de;
                        line-height: 1.3;
                        letter-spacing: -0.01em;
                    }
            
                    .error-message {
                        font-size: 1.1rem;
                        color: #1f2328;
                        background-color: #ddf4ff;   /* 浅蓝背景突出消息 */
                        padding: 1rem 1.5rem;
                        margin-bottom: 1.8rem;
                        border-left: 4px solid #0969da; /* 蓝色左边框点缀（不算边框，是装饰条） */
                        word-break: break-word;
                    }
            
                    pre {
                        background-color: #f6f8fa;
                        color: #24292f;
                        padding: 1.5rem 1.8rem;
                        overflow-x: auto;
                        font-family: Consolas, "Courier New", monospace;
                        font-size: 0.95rem;
                        line-height: 1.7;
                        white-space: pre-wrap;
                        word-break: break-word;
                        margin: 0;
                    }
            
                    /* 空 pre 不显示，避免空白区域 */
                    pre:empty {
                        display: none;
                    }
            
                    /* 让占位内容更柔和 */
                    .error-page > * {
                        max-width: 100vw;
                    }
            
                    /* 响应式调整 */
                    @media (max-width: 640px) {
                        body {
                            padding: 1rem 0;
                        }
                        .error-page {
                            padding: 1.5rem 1.2rem;
                        }
                        h1 {
                            font-size: 1.5rem;
                        }
                        .error-message {
                            font-size: 1rem;
                            padding: 0.9rem 1.2rem;
                        }
                        pre {
                            padding: 1rem 1.2rem;
                            font-size: 0.85rem;
                        }
                    }
            
                    /* 滚动条样式（蓝色系，弱化） */
                    pre::-webkit-scrollbar {
                        width: 8px;
                        height: 8px;
                    }
                    pre::-webkit-scrollbar-track {
                        background: #eaeef2;
                    }
                    pre::-webkit-scrollbar-thumb {
                        background: #9db9d4;
                        border-radius: 0;          /* 无圆角 */
                    }
                    pre::-webkit-scrollbar-thumb:hover {
                        background: #0969da;
                    }
                </style>
            </head>
            <body>
            <div class="error-page">
                <h1>%s</h1>
                <p class="error-message">%s</p>
                <pre>%s</pre>
            </div>
            </body>
            </html>
            """;

    private ExceptionMessageHelper() {
    }

    /**
     * 按通用模板生成错误页面文本：三段式结构，状态码、首部信息与堆栈，
     * 状态码由状态行的状态码与描述短语构成，首部信息与堆栈均经 HTML 实体转义后嵌入对应区块，
     * 堆栈为 null 或空字符串时堆栈区块保持为空，经样式隐藏不占版面。
     *
     * @param statusLine 状态行，提供页面标题的状态码与描述短语，不能为 null
     * @param header     首部信息文本，呈现为状态码下方的一句话说明
     * @param stack      堆栈文本，呈现为可横向滚动的 pre 区块，可为 null 或空字符串
     * @return 完整的 HTML 页面文本
     */
    public static String errorPage(StatusLine statusLine, String header, String stack) {
        Objects.requireNonNull(statusLine, "statusLine must not be null");
        String stackText = stack == null ? "" : htmlEscape(stack);
        String status = statusLine.code() + " " + statusLine.reason();
        return ERROR_PAGE_TEMPLATE.formatted(status, htmlEscape(header), stackText);
    }

    /**
     * 生成按给定 HTTP 状态码呈现的客户端错误页面文本：状态码标题由错误码携带的 HTTP 状态码构成，
     * 首部信息展示异常消息，消息为 null 时回落异常类名。
     * 该情况（路由未匹配、缺少必要参数等 4xx 错误码）不是服务器故障，页面不携带堆栈。
     *
     * @param throwable 触发客户端错误的异常
     * @param status    错误码携带的 HTTP 状态码，用于生成状态码标题
     * @return 完整的 HTML 页面文本
     */
    public static String clientErrorMessage(Throwable throwable, HttpResponseStatus status) {
        String message = throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage();
        return errorPage(StatusLine.of(status.code()), "Exception message: " + message, null);
    }

    /**
     * 生成服务器异常的 500 错误页面文本：首部信息展示异常类型，
     * 堆栈为完整异常栈，栈文本（含 Caused by 链）经 HTML 转义后嵌入可横向滚动的 pre 区块。
     *
     * @param throwable 触发兜底的原始异常
     * @return 完整的 HTML 页面文本
     */
    public static String errorMessage(Throwable throwable) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return errorPage(StatusLine.INTERNAL_SERVER_ERROR,
                "Exception type: " + throwable.getClass().getName(), writer.toString());
    }

    /**
     * 生成无受支持写出处理器的 500 错误页面文本：响应消息声明了写出职责，
     * 但内置与自定义两级策略组均未登记对应处理器，服务器无法写出该响应时以此页面兜底。
     *
     * @return 完整的 HTML 页面文本
     */
    public static String unsupportedWriteHandlerMessage() {
        return errorPage(StatusLine.INTERNAL_SERVER_ERROR, "No supported message write handler", null);
    }

    /**
     * 生成响应未携带 Content-Type 的 500 错误页面文本：
     * 响应消息到达写出处理器时缺少内容类型头，无法按契约写出时以此页面兜底。
     *
     * @return 完整的 HTML 页面文本
     */
    public static String missingContentTypeMessage() {
        return errorPage(StatusLine.INTERNAL_SERVER_ERROR, "Response message missing Content-Type", null);
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
