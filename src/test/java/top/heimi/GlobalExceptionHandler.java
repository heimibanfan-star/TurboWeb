package top.heimi;

import org.turbo.anno.ExceptionHandler;
import org.turbo.anno.ExceptionResponseStatus;

/**
 * TODO
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(NullPointerException.class)
    public String handle(NullPointerException e) {
        return e.getMessage();
    }

//    @ExceptionHandler(Exception.class)
//    public String doException(Exception e) {
//        return "网络请求超时";
//    }
}
