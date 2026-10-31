package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import top.turboweb.http.core.StatusLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExceptionMessageHelper} 的行为锚：统一三段式模板（状态码、首部信息、堆栈）下的
 * 异常响应页面文本契约——通用模板按状态行成题并承载首部信息与可选堆栈（空堆栈不占版面）、
 * 客户端错误页按给定状态码成题并展示转义后的异常消息（消息为 null 时回落异常类名）、
 * 500 页展示异常类型与转义后的完整栈文本（含 Caused by 链）、
 * 缺 Content-Type 兜底页说明缺少内容类型，
 * 五种 HTML 实体（&amp; &lt; &gt; &quot; &#39;）全部转义，外来文本不作为原始标签嵌入页面。
 */
class ExceptionMessageHelperTest {

    @Test
    void clientErrorMessageRendersTitleAndEscapesAllHtmlEntities() {
        String html = ExceptionMessageHelper.clientErrorMessage(
                new IllegalStateException("a&<>\"'b"), HttpResponseStatus.NOT_FOUND);

        assertTrue(html.contains("<!DOCTYPE html>"), "客户端错误页必须是完整 HTML 文档");
        assertTrue(html.contains("<h1>404 Not Found</h1>"), "标题由状态码与理由短语构成");
        assertTrue(html.contains("<p class=\"error-message\">Exception message: a&amp;&lt;&gt;&quot;&#39;b</p>"),
                "五种 HTML 实体都必须转义后嵌入消息段落");
        assertFalse(html.contains("a<>"), "原始尖括号不得透传给浏览器");
    }

    @Test
    void clientErrorMessageRendersTitlePerGivenStatus() {
        String html = ExceptionMessageHelper.clientErrorMessage(
                new IllegalStateException("bad param"), HttpResponseStatus.BAD_REQUEST);

        assertTrue(html.contains("<h1>400 Bad Request</h1>"), "标题随传入的状态码生成，不写死 404");
    }

    @Test
    void clientErrorMessageFallsBackToClassNameWhenMessageIsNull() {
        String html = ExceptionMessageHelper.clientErrorMessage(
                new IllegalStateException(), HttpResponseStatus.NOT_FOUND);

        assertTrue(html.contains("<p class=\"error-message\">Exception message: java.lang.IllegalStateException</p>"),
                "消息为 null 时以异常类名兜底展示");
    }

    @Test
    void errorMessageRendersTypeAndEscapedStackOfThrowable() {
        RuntimeException boom = new RuntimeException("<script>boom");

        String html = ExceptionMessageHelper.errorMessage(boom);

        assertTrue(html.contains("<h1>500 Internal Server Error</h1>"), "500 页必须带 500 标题");
        assertTrue(html.contains("Exception type: java.lang.RuntimeException</p>"), "类型段落必须展示异常全限定类名");
        assertTrue(html.contains("&lt;script&gt;boom"), "栈首行的消息必须经 HTML 转义");
        assertFalse(html.contains("<script>"), "原始脚本标签不得透传给浏览器");
        assertTrue(html.contains("at top.turboweb.http.orchestrator.ExceptionMessageHelperTest."),
                "响应文本必须携带完整栈（本测试类帧在栈内）");
    }

    @Test
    void errorMessageIncludesCausedByChain() {
        Exception outer = new Exception("outer", new IllegalStateException("inner<bad>"));

        String html = ExceptionMessageHelper.errorMessage(outer);

        assertTrue(html.contains("Caused by: java.lang.IllegalStateException: inner&lt;bad&gt;"),
                "栈文本必须含 Caused by 链且链上消息同样转义");
    }

    @Test
    void errorPageRendersThreePartsFromStatusLine() {
        String html = ExceptionMessageHelper.errorPage(StatusLine.NOT_FOUND, "Exception message: bad", "at test");

        assertTrue(html.contains("<h1>404 Not Found</h1>"), "状态码标题由状态行的状态码与描述短语构成");
        assertTrue(html.contains("<p class=\"error-message\">Exception message: bad</p>"), "首部信息呈现于消息段落");
        assertTrue(html.contains("<pre>at test</pre>"), "堆栈呈现于 pre 区块");
    }

    @Test
    void errorPageKeepsStackBlockEmptyWhenStackIsAbsent() {
        String html = ExceptionMessageHelper.errorPage(StatusLine.INTERNAL_SERVER_ERROR, "no stack", "");

        assertTrue(html.contains("<pre></pre>"), "空堆栈时堆栈区块保持为空，经样式隐藏不占版面");
    }

    @Test
    void errorPageEscapesHeaderAndStack() {
        String html = ExceptionMessageHelper.errorPage(StatusLine.INTERNAL_SERVER_ERROR, "a<b>&\"'", "x<y>");

        assertTrue(html.contains("a&lt;b&gt;&amp;&quot;&#39;"), "首部信息必须经 HTML 转义");
        assertTrue(html.contains("x&lt;y&gt;"), "堆栈文本必须经 HTML 转义");
        assertFalse(html.contains("<y>"), "原始标记不得透传给浏览器");
    }

    @Test
    void missingContentTypeMessageRendersMissingHeaderNotice() {
        String html = ExceptionMessageHelper.missingContentTypeMessage();

        assertTrue(html.contains("<h1>500 Internal Server Error</h1>"), "缺 Content-Type 兜底页带 500 状态码标题");
        assertTrue(html.contains("Response message missing Content-Type"), "首部信息说明响应缺少 Content-Type");
        assertTrue(html.contains("<pre></pre>"), "无堆栈时堆栈区块保持为空");
    }
}
