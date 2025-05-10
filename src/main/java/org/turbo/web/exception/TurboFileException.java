package org.turbo.web.exception;

/**
 * 文件操作相关的异常
 */
public class TurboFileException extends RuntimeException {

	public TurboFileException(String message) {
		super(message);
	}

	public TurboFileException(Throwable e) {
		super(e);
	}

	public TurboFileException(String message, Throwable e) {
		super(message, e);
	}
}
