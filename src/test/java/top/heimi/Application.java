package top.heimi;

import org.turbo.web.core.config.ServerParamConfig;
import org.turbo.web.core.http.middleware.CorsMiddleware;
import org.turbo.web.core.server.TurboServer;
import org.turbo.web.core.server.impl.DefaultTurboServer;
import top.heimi.controller.HelloController;
import top.heimi.handler.GlobalExceptionHandler;
import top.heimi.handler.ReaGlobalExceptionHandler;

/**
 * TODO
 */
public class Application {

    private int num = 10;

    public static void main(String[] args) {
        TurboServer server = new DefaultTurboServer(Application.class);
        server.addController(new HelloController());
        server.setIsReactiveServer(true);
        ServerParamConfig config = new ServerParamConfig();
        config.setShowRequestLog(false);
        server.addMiddleware(new CorsMiddleware());
//        server.setConfig(config);
        server.addExceptionHandler(new ReaGlobalExceptionHandler());
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