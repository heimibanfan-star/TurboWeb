package top.turboweb.commons.limit;

/**
 * 令牌桶接口
 */
public interface TokenBucket {

    /**
     * 尝试获取令牌
     * @return 是否成功获取令牌
     */
    boolean tryAcquire();

}
