package top.heimi;

import org.turbo.anno.ExceptionHandler;

/**
 * TODO
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(NullPointerException.class)
    public String handle(NullPointerException e) {
        return e.getMessage();
    }
}
