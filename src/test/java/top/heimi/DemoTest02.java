package top.heimi;

import com.google.common.reflect.TypeToken;
import org.turbo.core.router.container.RouterContainer;
import org.turbo.utils.init.RouterContainerInitUtils;

import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO
 */
public class DemoTest02 {
    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
//        List<Class<?>> controllerList = new ArrayList<>();
//        controllerList.add(TestClass.class);
//        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllerList);
        String url = "/user";
        String regex = "/user/([^/]*)/([^/]*)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(url);
        System.out.println(matcher.groupCount());
        System.out.println(matcher.find());
//        Class<TestClass> testClassClass = TestClass.class;
//        TestClass obj = new TestClass();
//        Method test2 = testClassClass.getMethod("test2", int.class);
//        Map<String, Objects> map = new HashMap<>();
//        System.out.println(map.get("hello"));
//        Class<RefClass> refClassClass = RefClass.class;
//        Method[] methods = refClassClass.getMethods();
//        for (Method method : methods) {
//            if (method.getName().equals("test")) {
//                Parameter[] parameters = method.getParameters();
//                for (Parameter parameter : parameters) {
//                    Type parameterizedType = parameter.getParameterizedType();
//                    String typeName = parameterizedType.getTypeName();
//                    System.out.println(typeName);
//                }
//            }
//        }
        String s = "java.util.List";
        Class<?> aClass = Class.forName(s);
    }
}
