package top.turboweb.commons.utils.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 错误页模板：整串精确比较，格式改动必须让这条红。
 */
class ErrorStrGeneratorTest {

    @Test
    void errHtmlIsTheExactTemplate() {
        assertEquals("<h1>TurboWeb Error, Code: 500</h1>\nerrMsg: boom\n",
                ErrorStrGenerator.errHtml(500, "boom"));
    }

    @Test
    void messageIsNotEscaped() {
        assertEquals("<h1>TurboWeb Error, Code: 400</h1>\nerrMsg: <script>\n",
                ErrorStrGenerator.errHtml(400, "<script>"),
                "工具不做 HTML 转义：调用方若把用户输入直接塞进来就是 XSS 面（阶段 4 的响应策略用例复看）");
    }
}
