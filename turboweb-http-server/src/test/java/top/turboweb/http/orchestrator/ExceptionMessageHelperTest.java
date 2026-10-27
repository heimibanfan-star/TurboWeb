package top.turboweb.http.orchestrator;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExceptionMessageHelper} 的行为锚：两类异常响应页面的文本契约——
 * 客户端错误页按给定状态码成题并展示转义后的异常消息（消息为 null 时回落异常类名）、
 * 500 页展示异常类型与转义后的完整栈文本（含 Caused by 链），
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
}
