package top.heimi.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * TODO
 */
public class Application {
    public static void main(String[] args) {
        Object object = Proxy.newProxyInstance(
                UserController.class.getClassLoader(),
                new Class[]{UserController.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        System.out.println("hello world");
                        return null;
                    }
                }
        );
        UserController userController = UserController.class.cast(object);
        userController.show();
    }
}
