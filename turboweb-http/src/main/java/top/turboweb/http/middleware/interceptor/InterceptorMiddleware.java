package top.turboweb.http.middleware.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.struct.trie.PatternPathTrie;
import top.turboweb.commons.utils.order.QuickSortUtils;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.Middleware;

import java.util.*;

/**
 * 用于实现拦截器的中间件
 */
public class InterceptorMiddleware extends Middleware {

    private static final Logger log = LoggerFactory.getLogger(InterceptorMiddleware.class);
    // 用于存储用户注册的拦截器
    private final Map<String, List<InterceptorHandler>> interceptorHandlers = new HashMap<>();
    // 防止排序重复
    private final Set<Integer> orders = new HashSet<>();
    // 以前缀树的方式存储用户注册的拦截器
    private final PatternPathTrie<List<InterceptorHandler>> pathTrie = new PatternPathTrie<>();

    @Override
    public Object invoke(HttpContext ctx) {
        // 获取请求的路径
        String uri = ctx.getRequest().getUri();
        // 匹配所有的拦截器
        InterceptorHandler[] interceptors = matchInterceptors(uri);
        int passIndex = -1;
        Object result = null;
        Throwable e = null;
        try {
            for (int i = 0; i < interceptors.length; i++) {
                boolean ok = interceptors[i].preHandler(ctx);
                if (!ok) {
                    break;
                }
                passIndex = i;
            }
            // 判断是否所有的前置拦截器都执行成功
            if (passIndex == interceptors.length - 1) {
                // 调用后续的中间件
                result = next(ctx);
                // 执行后置
                for (int i = passIndex; i >= 0; i--) {
                    result = interceptors[i].postHandler(ctx, result);
                }
            }
        } catch (Throwable throwable) {
            e = throwable;
            throw throwable;
        } finally {
            // 执行请求结束处理器
            for (int i = passIndex; i >= 0; i--) {
                interceptors[i].afterCompletion(e);
            }
        }
        return result;
    }

    /**
     * 添加拦截器
     *
     * @param pathPattern 拦截器可以处理的路径表达式
     * @param interceptor 拦截器对象
     */
    public void addInterceptionHandler(String pathPattern, InterceptorHandler interceptor) {
        if (pathPattern == null || interceptor == null) {
            throw new NullPointerException("pathPattern and interceptor must not null");
        }
        if (orders.contains(interceptor.order())) {
            throw new IllegalArgumentException("order must be unique");
        }
        orders.add(interceptor.order());
        interceptorHandlers.computeIfAbsent(pathPattern, k -> new ArrayList<>());
        interceptorHandlers.get(pathPattern).add(interceptor);
    }

    @Override
    public void init(Middleware chain) {
        interceptorHandlers.forEach(pathTrie::insert);
        log.info("interceptor init success");
    }

    /**
     * 根据请求的地址匹配所有的拦截器
     *
     * @param path 请求的地址
     * @return 匹配到的所有拦截器
     */
    private InterceptorHandler[] matchInterceptors(String path) {
        // 去除路径的参数部分
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // 匹配所有的拦截器
        Set<List<InterceptorHandler>> matchResult = pathTrie.matchAllValues(path);
        int count = 0;
        for (List<InterceptorHandler> handlers : matchResult) {
            count += handlers.size();
        }
        // 创建数组
        InterceptorHandler[] interceptors = new InterceptorHandler[count];
        // 将所有的拦截器添加到数组中
        int index = 0;
        for (List<InterceptorHandler> handlers : matchResult) {
            for (InterceptorHandler handler : handlers) {
                interceptors[index++] = handler;
            }
        }
        // 对拦截器进行排序
        QuickSortUtils.sort(interceptors);
        return interceptors;
    }
}
