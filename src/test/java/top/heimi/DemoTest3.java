package top.heimi;

import java.util.UUID;

/**
 * TODO
 */
public class DemoTest3 {
    public static void main(String[] args) {
        // 生成JSESSIONID
        UUID uuid = UUID.randomUUID();
        String jsessionid = uuid.toString().replace("-", "");
        System.out.println(jsessionid);
    }
}
