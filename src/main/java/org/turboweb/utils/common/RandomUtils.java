package org.turboweb.utils.common;

import java.util.UUID;

/**
 * 随机内容工具类
 */
public class RandomUtils {

    private RandomUtils() {
    }

    /**
     * 生成随机的uuid
     *
     * @return uuid
     */
    public static String uuidWithoutHyphen() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString().replace("-", "");
    }
}
