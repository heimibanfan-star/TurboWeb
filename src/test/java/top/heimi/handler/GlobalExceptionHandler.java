package top.heimi.handler;

import org.turbo.web.anno.ExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 异常处理器
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Mono<String> handleException(Exception e) {
        return Mono.just("发生错误：" + e.getMessage());
    }
}
