package top.turboweb.http.orchestrator.convert;

import top.turboweb.http.core.ByteResponseMessage;
import top.turboweb.http.core.StatusLine;

import java.nio.charset.Charset;

/** 内置环节：认领 String，按照 text/html 装配字节消息。 */
public final class StringConvertHandler implements ConvertHandler {

    /** Content-Type 头名 */
    private static final String CONTENT_TYPE = "Content-Type";

    @Override
    public ConvertHandler.Result convert(Object result, Charset charset) {
        return result instanceof String string
                ? ConvertHandler.Result.finished(buildByteResponse(string, "text/html;charset=" + charset.name(), charset))
                : ConvertHandler.Result.shouldContinue();
    }

    /**
     * 构建字节响应消息：正文按给定字符集编码，Content-Type 由调用点声明完整，
     * 内容长度交由写出环节按实际响应体补齐。
     *
     * @param content     内容
     * @param contentType 内容类型
     * @param charset     正文编码字符集
     * @return 字节响应消息
     */
    private static ByteResponseMessage buildByteResponse(String content, String contentType, Charset charset) {
        ByteResponseMessage message = new ByteResponseMessage(StatusLine.OK, content.getBytes(charset));
        message.headers().set(CONTENT_TYPE, contentType);
        return message;
    }
}
