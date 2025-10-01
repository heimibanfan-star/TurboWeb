package top.turboweb.gateway.breaker;

import java.util.Set;

/**
 * 断路器
 */
public interface Breaker {

    /**
     * 获取超时时间
     * @return 超时时间
     */
    long getTimeout();

    /**
     * 设置失败
     * @param uri 请求uri
     */
    void setFail(String uri);

    /**
     * 设置成功
     * @param uri 请求uri
     */
    void setSuccess(String uri);

    /**
     * 是否断路
     * @param uri 请求uri
     * @return 是否断路
     */
    boolean isBreak(String uri);

    /**
     * 认为失败的验证码
     * @return 失败状态码
     */
    Set<Integer> failStatusCode();
}
