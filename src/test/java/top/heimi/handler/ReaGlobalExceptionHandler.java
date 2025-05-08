package top.heimi.handler;

import org.turbo.web.anno.ExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * TODO
 */
public class ReaGlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public Mono<String> handle(Exception e) {
        return Mono.just("error");
    }
}
