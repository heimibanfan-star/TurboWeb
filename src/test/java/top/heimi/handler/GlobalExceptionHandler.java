package top.heimi.handler;

import org.turbo.web.anno.ExceptionHandler;
import org.turbo.web.exception.TurboArgsValidationException;
import reactor.core.publisher.Mono;
import top.heimi.pojos.Result;

import java.util.List;

/**
 * 异常处理器
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Mono<Result<?>> handleException(Exception e) {
        return Mono.just(new Result<>(500, null, e.getMessage()));
    }

//    @ExceptionHandler(TurboArgsValidationException.class)
//    public Mono<Result<?>> handleTurboArgsValidationException(TurboArgsValidationException e) {
//        List<String> errorMsg = e.getErrorMsg();
//        StringBuilder errors = new StringBuilder();
//        for (String s : errorMsg) {
//            errors.append(s).append(";");
//        }
//        return Mono.just(new Result<>(400, null, errors.toString()));
//    }
}
