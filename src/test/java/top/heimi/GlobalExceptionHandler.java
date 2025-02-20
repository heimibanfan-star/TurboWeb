package top.heimi;

import org.turbo.anno.ExceptionHandler;
import org.turbo.anno.ExceptionResponseStatus;
import org.turbo.exception.TurboArgsValidationException;

import java.util.List;

/**
 * TODO
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(NullPointerException.class)
    public String handle(NullPointerException e) {
        return e.getMessage();
    }

    @ExceptionHandler(TurboArgsValidationException.class)
    public String handle(TurboArgsValidationException e) {
        List<String> errorMsg = e.getErrorMsg();
        StringBuilder sb = new StringBuilder();
        for (String s : errorMsg) {
            sb.append(s).append(";");
        }
        return sb.toString();
    }

//    @ExceptionHandler(Exception.class)
//    public String doException(Exception e) {
//        return "网络请求超时";
//    }
}
