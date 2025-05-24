package top.heimi.handlers;

import org.turboweb.anno.ExceptionHandler;

/**
 * 异常处理器
 */
public class GlobalExceptionHandler {

	@ExceptionHandler(Exception.class)
	public String handleException(Exception e) {
		return e.getMessage();
	}
}
