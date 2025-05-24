package org.turboweb.exception;

/**
 * 服务器初始化过程中出现的异常
 */
public class TurboServerInitException extends RuntimeException{

    public TurboServerInitException(String message) {
        super(message);
    }
}
