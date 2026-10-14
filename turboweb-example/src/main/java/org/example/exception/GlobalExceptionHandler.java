package org.example.exception;


import top.turboweb.http.handler.anno.ExceptionHandler;
import top.turboweb.http.handler.anno.ExceptionResponseStatus;
import top.turboweb.http.exception.ParameterValidationException;

import java.util.Map;

public class GlobalExceptionHandler {
    @ExceptionHandler(ParameterValidationException.class)
    @ExceptionResponseStatus(400)
    public Map<String, String> doParameterValidationException(ParameterValidationException e) {
        StringBuilder errMsg = new StringBuilder();
        for (String s : e.getErrorMsg()) {
            errMsg.append(s).append(";");
        }
        return Map.of(
                "message", errMsg.toString()
        );
    }
}
