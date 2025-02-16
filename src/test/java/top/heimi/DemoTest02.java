package top.heimi;

import org.turbo.core.router.container.RouterContainer;
import org.turbo.utils.init.RouterContainerInitUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO
 */
public class DemoTest02 {
    public static void main(String[] args) throws NoSuchMethodException {
        List<Class<?>> controllerList = new ArrayList<>();
        controllerList.add(TestClass.class);
        RouterContainer routerContainer = RouterContainerInitUtils.ininContainer(controllerList);
    }
}
