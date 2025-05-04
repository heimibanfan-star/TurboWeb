package top.heimi;

import io.netty.handler.codec.http.HttpMethod;
import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.middleware.*;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.UserController;
import top.heimi.handler.GlobalExceptionHandler;
import top.heimi.middleware.GlobalLimitMiddleware;
import top.heimi.middleware.LimitMiddleware;
import top.heimi.middleware.TestMiddleware;
import top.heimi.ws.MyWebSocketHandler;

import java.lang.invoke.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TODO
 */
public class Application {

    private int num = 10;

    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class);
        CorsMiddleware cors = new CorsMiddleware();
        // 指定允许的跨域来源
        cors.setAllowedOrigins(List.of("https://example.com"));
        // 指定允许的 HTTP 方法
        cors.setAllowedMethods(List.of("GET", "POST"));
        // 指定允许的请求头
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        // 指定哪些响应头可以暴露给客户端
        cors.setExposedHeaders(List.of("Content-Disposition"));
        // 是否允许携带 Cookie
        cors.setAllowCredentials(true);
        // 设置预检请求的缓存时间（单位：秒）
        cors.setMaxAge(1800);
        server.addMiddleware(cors);
        server.start();
    }
//
//    private static void testCache(MethodHandle handle) throws Throwable {
//        long start = System.nanoTime();
//        for (int i = 0; i < 10000000; i++) {
//            handle.invoke();
//        }
//        System.out.println(System.nanoTime() - start);
//    }
//
//    private static void testInvoke(Method method, Object obj) throws InvocationTargetException, IllegalAccessException {
//        long start = System.nanoTime();
//        for (int i = 0; i < 10000000; i++) {
//            method.invoke(obj);
//        }
//        System.out.println(System.nanoTime() - start);
//    }
//
//    private static void testUse(User user) {
//        long start = System.nanoTime();
//        for (int i = 0; i < 10000000; i++) {
//            user.getName();
//        }
//        System.out.println(System.nanoTime() - start);
//    }
}

//23353800
//18881400
//15413400
//15533600
//15101900
//22710100
//18616500
//17653100
//20985200
//19091000