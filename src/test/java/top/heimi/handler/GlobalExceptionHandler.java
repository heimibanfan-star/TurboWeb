package top.heimi.handler;

import org.turbo.web.anno.ExceptionHandler;

/**
 * 异常处理器
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        e.printStackTrace();
        return "<h1>500</h1>";
    }
}
