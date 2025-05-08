package org.turbo.web.exception;

/**
 * 方法调用异常
 */
public class TurboMethodInvokeThrowable extends Throwable {

    public TurboMethodInvokeThrowable(String message) {
        super(message);
    }

    public TurboMethodInvokeThrowable(Throwable e) {
        super(e);
    }
}
