package org.example.exceptionhandler;

import top.turboweb.commons.anno.ExceptionHandler;
import top.turboweb.commons.anno.ExceptionResponseStatus;
import top.turboweb.commons.exception.TurboArgsValidationException;

import java.util.Map;

/**
 * 全局异常处理器
 */
public class GlobalExceptionHandler {
	@ExceptionHandler(RuntimeException.class)
	@ExceptionResponseStatus(500)
	public Map<String, String> doRuntimeException(RuntimeException e) {
		return Map.of("message", "runtimeException");
	}

	@ExceptionHandler(TurboArgsValidationException.class)
	public Map<String, String> doTurboArgsValidationException(TurboArgsValidationException e) {
		StringBuilder errMsg = new StringBuilder();
		for (String s : e.getErrorMsg()) {
			errMsg.append(s).append(";");
		}
		return Map.of("message", errMsg.toString());
	}
}
