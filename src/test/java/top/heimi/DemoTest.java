package top.heimi;

import java.net.URI;

/**
 * TODO
 */
public class DemoTest {
    public static void main(String[] args) {
        String url = "../../hello";
        URI uri = URI.create(url);
        System.out.println(uri.getPath());
    }
}
