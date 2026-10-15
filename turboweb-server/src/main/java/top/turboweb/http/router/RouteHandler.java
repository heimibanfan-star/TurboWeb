package top.turboweb.http.router;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.validation.BeanValidator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 路由处理器
 */
public class RouteHandler {

    private static final ParameterParser.Binder DEFAULT_PARAMETER_BINDER = (ctx, beanValidator) -> null;
    private final String path;

    private final RouteHandlerInvoker[] invokers;

    /**
     * Bean 校验器,调用绑定器时逐次传入;绑定结果为 bean 且需要校验时由绑定器经其执行
     */
    private final BeanValidator beanValidator;

    /**
     * 构建路由处理器:逐条元信息解析参数绑定器并展开目标方法句柄
     *
     * @param metaInfos 路由元信息列表,至少一条;各条 url 必须一致,同一请求方式至多出现一次
     * @param parsers 参数解析器列表,按注册顺序依次认领参数,不能为 null
     * @param beanValidator Bean 校验器,调用参数绑定器时传入,绑定结果为 bean 需校验时由绑定器经其执行,不能为 null
     * @throws IllegalArgumentException 元信息为空、url 不一致或同一请求方式重复注册
     * @throws NullPointerException parsers 或 beanValidator 为 null
     * @throws RouteHandlerException 目标方法不可访问导致展开失败,错误码为 ROUTER_DEFINITION_CREATE_ERROR
     */
    public RouteHandler(List<RouteHandlerMetaInfo> metaInfos, List<ParameterParser> parsers, BeanValidator beanValidator) {
        if (metaInfos == null || metaInfos.isEmpty()) {
            throw new IllegalArgumentException("RouteHandlerMetaInfo must not be empty");
        }
        // 解析器列表必须显式给出,不接受 null,在构造入口拒绝而非留到逐参数解析时深处失败
        Objects.requireNonNull(parsers, "parsers must not be null");
        this.beanValidator = Objects.requireNonNull(beanValidator, "beanValidator must not be null");
        this.path = metaInfos.getFirst().url();
        // 判断是否有重复的请求方式
        Set<HttpMethod> httpMethods = new HashSet<>();
        List<RouteHandlerInvoker> invokerList = new ArrayList<>();
        for (RouteHandlerMetaInfo metaInfo : metaInfos) {
            // 一个处理器只承载一条路径,混入不同 url 在构造期拒绝
            if (!Objects.equals(this.path, metaInfo.url())) {
                throw new IllegalArgumentException("Route url must be consistent: " + this.path + " != " + metaInfo.url());
            }
            if (httpMethods.contains(metaInfo.httpMethod())) {
                throw new IllegalArgumentException("Route repeat:" + metaInfo.httpMethod() + " " + metaInfo.url());
            }
            Method method = metaInfo.method();
            // 获取参数绑定器
            ParameterParser.Binder[] binders = parseBinders(method, parsers);
            try {
                // 展开方法句柄
                MethodHandle methodHandle = MethodHandles.lookup().unreflect(method).bindTo(metaInfo.instance()).asSpreader(Object[].class, method.getParameterCount());
                invokerList.add(new RouteHandlerInvoker(metaInfo.httpMethod(), methodHandle, binders));
                httpMethods.add(metaInfo.httpMethod());
            } catch (IllegalAccessException e) {
                throw new RouteHandlerException(e, RouteHandlerException.ROUTER_DEFINITION_CREATE_ERROR);
            }
        }
        this.invokers = invokerList.toArray(new RouteHandlerInvoker[]{});
    }

    /**
     * 为方法的形参逐位选取参数绑定器
     *
     * @param method 目标方法
     * @param parsers 解析器列表,按序尝试,首个返回非空结果的解析器生效
     * @return 与形参一一对应的绑定器数组;无人认领的参数取缺省绑定器,其绑定值为 null,
     *          原始类型形参因此会在调用时拆箱失败
     */
    private ParameterParser.Binder[] parseBinders(Method method, List<ParameterParser> parsers) {
        Parameter[] parameters = method.getParameters();
        ParameterParser.Binder[] binders = new ParameterParser.Binder[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterParser.Binder binder = null;
            // 依次解析所有的参数
            for (ParameterParser parser : parsers) {
                binder = parser.parse(parameters[i]);
                if (binder != null) {
                    break;
                }
            }
            if (binder == null) {
                binder = DEFAULT_PARAMETER_BINDER;
            }
            binders[i] = binder;
        }
        return binders;
    }

    /**
     * 处理请求:按请求方式匹配调用器并执行
     *
     * @param context 请求上下文
     * @return 目标方法的返回值
     * @throws RouteHandlerException 请求方式与已登记的均不匹配,错误码为 ROUTER_NOT_MATCH
     * @throws Throwable 目标方法抛出的异常原样透传
     */
    public Object handle(HttpContext context) throws Throwable {
        FullHttpRequest request = context.getRequest();
        HttpMethod httpMethod = request.method();
        // 按请求方式匹配调用器,命中即绑定参数并展开调用目标方法
        for (RouteHandlerInvoker invoker : invokers) {
            if (Objects.equals(invoker.httpMethod(), httpMethod)) {
                return invoker.invoke(context, beanValidator);
            }
        }
        // 无匹配的请求方式,按路由不匹配错误码抛出
        throw new RouteHandlerException("not found route:" + httpMethod.name() + " " + request.uri(), RouteHandlerException.ROUTER_NOT_MATCH);
    }

    /**
     * 本处理器承载的路径
     *
     * @return 首条元信息的 url
     */
    public String path() {
        return this.path;
    }

}
