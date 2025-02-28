package org.turbo.web.exception;

/**
 * 模板渲染异常
 */
public class TurboTemplateRenderException extends RuntimeException {
    public TurboTemplateRenderException(String message) {
        super(message);
    }

    public TurboTemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
