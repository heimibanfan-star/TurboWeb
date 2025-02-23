package top.heimi;

import org.turbo.web.utils.common.BeanUtils;

import java.lang.reflect.*;
import java.util.*;

/**
 * TODO
 */
public class DemoTest02 {
    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException {
//        List<Class<?>> controllerList = new ArrayList<>();
//        controllerList.add(TestClass.class);
//        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllerList);
//        String url = "/user";
//        String regex = "/user/([^/]*)/([^/]*)";
//        Pattern pattern = Pattern.compile(regex);
//        Matcher matcher = pattern.matcher(url);
//        System.out.println(matcher.groupCount());
//        System.out.println(matcher.find());
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
//        String s = "java.util.List";
//        Class<?> aClass = Class.forName(s);
        Map<String, Object> map = new HashMap<>();
        map.put("name", "zhangsan");
        map.put("sex", "男");
        User user = new User();
        BeanUtils.mapToBean(map, user);
        System.out.println(user);
    }
}
