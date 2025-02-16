package top.heimi;

import org.turbo.core.router.container.RouterContainer;
import org.turbo.utils.init.RouterContainerInitUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO
 */
public class DemoTest02 {
    public static void main(String[] args) throws NoSuchMethodException {
        List<Class<?>> controllerList = new ArrayList<>();
        controllerList.add(TestClass.class);
        RouterContainer routerContainer = RouterContainerInitUtils.initContainer(controllerList);
    }
}
