package top.turboweb.http.middleware.limiter;

import io.netty.handler.codec.http.*;
import top.turboweb.commons.limit.TokenBucket;
import top.turboweb.commons.struct.trie.PatternPathTrie;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 基于路径的限流器
 */
public class PathLimiter extends Middleware {

    private final PatternPathTrie<TokenBucket> pathTrie = new PatternPathTrie<>();


    @Override
    public final Object invoke(HttpContext ctx) {
        String path = ctx.getRequest().getUri();
        // 删除路径参数
        path = path.substring(0, path.indexOf("?"));
        if (path.endsWith("/") && !"/".equals(path)) {
            path = path.substring(0, path.length() - 1);
        }
        // 匹配规则
        Set<TokenBucket> tokenBuckets = pathTrie.patternMatch(path);
        // 没有规则直接放行
        if (tokenBuckets.isEmpty()) {
            return next(ctx);
        }
        // 获取第一个规则
        TokenBucket tokenBucket = tokenBuckets.iterator().next();
        // 进行限流处理
        boolean acquired = tokenBucket.tryAcquire();
        // 拦截请求
        if (!acquired) {
            return doReject(ctx);
        }
        // 正常放行
        return next(ctx);
    }

    /**
     * 添加限流规则
     *
     * @param path           路径
     * @param limit          限制数量
     * @param intervalSeconds 时间间隔
     */
    public final void addRule(String path, int limit, long intervalSeconds) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path can not be empty");
        }
        if (path.endsWith("/") && !"/".equals(path)) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.contains("{") || path.contains("}")) {
            throw new IllegalArgumentException("path can not contains '{}'");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be greater than 0");
        }
        pathTrie.insert(path, new TokenBucket(limit, intervalSeconds));
    }

    /**
     * 拒绝访问
     *
     * @param ctx 请求上下文
     * @return 响应对象
     */
    protected HttpResponse doReject(HttpContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS);
        String msg = "too many request";
        response.content().writeCharSequence(msg, StandardCharsets.UTF_8);
        response.headers().add("Content-Length", msg.length());
        return response;
    }
}
