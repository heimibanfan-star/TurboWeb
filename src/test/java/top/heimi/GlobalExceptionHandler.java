package top.heimi;

import org.turbo.web.anno.ExceptionHandler;
import org.turbo.web.exception.TurboArgsValidationException;

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
