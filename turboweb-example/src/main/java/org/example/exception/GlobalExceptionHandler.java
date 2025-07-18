package org.example.exception;

import top.turboweb.commons.anno.ExceptionHandler;
import top.turboweb.commons.anno.ExceptionResponseStatus;
import top.turboweb.commons.exception.TurboArgsValidationException;

import java.util.Map;

public class GlobalExceptionHandler {
    @ExceptionHandler(TurboArgsValidationException.class)
    @ExceptionResponseStatus(400)
    public Map<String, String> doTurboArgsValidationException(TurboArgsValidationException e) {
        StringBuilder errMsg = new StringBuilder();
        for (String s : e.getErrorMsg()) {
            errMsg.append(s).append(";");
        }
        return Map.of(
                "message", errMsg.toString()
        );
    }
}
