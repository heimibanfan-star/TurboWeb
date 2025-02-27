package top.heimi;

import java.io.InputStream;
import java.net.URI;

/**
 * TODO
 */
public class DemoTest {
    public static void main(String[] args) {
        InputStream is = DemoTest.class.getClassLoader().getResourceAsStream("/static/index.html");
        System.out.println(is);
    }
}
