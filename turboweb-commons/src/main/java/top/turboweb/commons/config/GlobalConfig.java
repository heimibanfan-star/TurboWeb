package top.turboweb.commons.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 全局配置
 */
public class GlobalConfig {

    private static final Logger log = LoggerFactory.getLogger(GlobalConfig.class);
    private static boolean isLock = false;
    private static final ReentrantLock lock = new ReentrantLock();

    private static Charset requestCharset = StandardCharsets.UTF_8;

    private static Charset responseCharset = StandardCharsets.UTF_8;

    /**
     * 锁定全局配置，不允许修改
     */
    public static void lockConfig() {
        lock.lock();
        try {
            isLock = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 设置全局请求字符集
     *
     * @param charset 字符集
     */
    public static void setRequestCharset(Charset charset) {
        lock.lock();
        try {
            if (isLock) {
                log.warn("GlobalConfig is locked, can not set request charset");
                return;
            }
            requestCharset = charset;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 设置全局响应字符集
     *
     * @param charset 字符集
     */
    public static void setResponseCharset(Charset charset) {
        lock.lock();
        try {
            if (isLock) {
                log.warn("GlobalConfig is locked, can not set response charset");
                return;
            }
            responseCharset = charset;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取全局请求字符集
     *
     * @return 字符集
     */
    public static Charset getRequestCharset() {
        return requestCharset;
    }

    /**
     * 获取全局响应字符集
     *
     * @return 字符集
     */
    public static Charset getResponseCharset() {
        return responseCharset;
    }
}
