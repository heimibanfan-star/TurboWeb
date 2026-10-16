package top.turboweb.http.router;

import top.turboweb.http.context.HttpContext;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.router.parameter.*;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.struct.radix.RestRouteTree;
import top.turboweb.struct.radix.RouteTree;
import top.turboweb.validation.BeanValidator;
import top.turboweb.validation.HibernateBeanValidator;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * rest 风格的路由登记表：注解路由与各项扩展点在此登记，生产路由处理器管理器的只读快照。
 *
 * <p>装配口径：登记的控制器按 url 解析元信息并聚合，同一 url 的各请求方式合进一个
 * {@link RouteHandler}，再以 url 为模式登记进基数树；路径非法、同路径同请求方式重复、
 * 或模式与既有注册面歧义，都在 {@link #newRouteHandlerManager(JsonSerializer)} 时点直接失败。
 *
 * <p>生产一次即冻结：门闩落下之后，重复生产与一切登记、剔除、替换、指定校验器的入口都抛
 * {@link IllegalStateException}，已生产出的管理器不会漏看后续登记，也不会被后续登记改动。
 *
 * <pre>{@code
 * RouteHandlerManager manager = new RestRouteRegister("/api")
 *         .registerRoute(new OrderController())
 *         .newRouteHandlerManager(jsonSerializer);
 * }</pre>
 */
public class RestRouteRegister implements RouteRegister {

    private static final String SPLIT_PATH = "/";

    /**
     * 门闩落下后所有入口共用的拒绝文案
     */
    private static final String PRODUCED_MESSAGE = "Route register is already produced";

    /**
     * 默认 Bean 校验器:未指定时由生产环节采用,Hibernate 引擎线程安全,全局共享一份
     */
    private static final BeanValidator DEFAULT_BEAN_VALIDATOR = new HibernateBeanValidator();

    /**
     * 路由前缀,构造期已裁去冗余尾斜杠
     */
    private final String baseUrl;
    /**
     * 类到实例的登记表,按登记类判重
     */
    private final Map<Class<?>, Object> routeInstanceMap = new HashMap<>();
    /**
     * 参数解析器表,构造期装入默认十枚,按表序依次认领形参
     */
    private final LinkedList<ParameterParser> parsers = new LinkedList<>();

    /**
     * Bean 校验器,null 表示未指定,生产时采用默认校验器
     */
    private BeanValidator beanValidator;

    /**
     * 生产门闩:一个登记表至多生产一个管理器,落下之后所有变更入口一律拒绝
     */
    private final AtomicBoolean produced = new AtomicBoolean(false);

    /**
     * 构建路由登记表
     *
     * <p>前缀只裁冗余的尾部斜杠:连续斜杠会被路径合并判为非法路径,而尾斜杠属于可规整的冗余。
     * 缺前导斜杠与根路径形态不在此处理,统一由合并环节的规整负责。
     *
     * @param baseUrl 路由前缀,null 与空串视为无前缀;含连续斜杠时于生产阶段按非法路径拒绝
     */
    public RestRouteRegister(String baseUrl) {
        // 对基础地址进行预处理
        baseUrl = Optional.ofNullable(baseUrl).orElse("");
        while (baseUrl.endsWith(SPLIT_PATH)) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.baseUrl = baseUrl;
        // 注册默认的参数解析器
        parsers.add(new InternTypeParameterParser());
        parsers.add(new PathParameterParser());
        parsers.add(new QueryParameterParser());
        parsers.add(new UploadParameterParser());
        parsers.add(new BeanQueryParameterParser());
        parsers.add(new BeanFormParameterParser());
        parsers.add(new BeanJsonParameterParser());
        parsers.add(new CookieParameterParser());
        parsers.add(new FormParameterParser());
        parsers.add(new HeaderParameterParser());
    }

    /**
     * 构建无前缀的路由登记表
     */
    public RestRouteRegister() {
        this(null);
    }

    /**
     * 注册路由:注解按显式给出的类扫描,调用落在给定实例上
     *
     * @param targetClass 注解扫描的类,不能为 null
     * @param instance 路由处理器实例,不能为 null
     * @return 本登记表
     * @throws NullPointerException targetClass 或 instance 为 null
     * @throws IllegalStateException 本登记表已生产过管理器,或 targetClass 已注册过
     */
    public RestRouteRegister registerRoute(Class<?> targetClass, Object instance) {
        checkNotProduced();
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        Objects.requireNonNull(instance, "routeInstance must not be null");
        if (this.routeInstanceMap.containsKey(targetClass)) {
            throw new IllegalStateException(targetClass + " is already registered");
        }
        this.routeInstanceMap.put(targetClass, instance);
        return this;
    }

    /**
     * 注册路由实例,注解扫描取实例运行时的类
     *
     * @param instance 路由处理器实例,不能为 null
     * @return 本登记表
     * @throws NullPointerException instance 为 null
     * @throws IllegalStateException 本登记表已生产过管理器,或实例的类已注册过
     */
    public RestRouteRegister registerRoute(Object instance) {
        Objects.requireNonNull(instance, "routeInstance must not be null");
        return registerRoute(instance.getClass(), instance);
    }

    /**
     * 批量注册路由实例,逐个按各自的运行时类登记
     *
     * @param instances 路由处理器实例列表,null 按无注册处理
     * @return 本登记表
     * @throws NullPointerException 列表中出现 null 元素
     * @throws IllegalStateException 本登记表已生产过管理器,或列表中出现同类实例
     */
    public RestRouteRegister registerRoute(List<Object> instances) {
        for (Object instance : Optional.ofNullable(instances).orElse(List.of())) {
            registerRoute(instance);
        }
        return this;
    }

    /**
     * 批量注册路由表,每个条目按类与实例成对登记
     *
     * @param routes 类到实例的映射
     * @return 本登记表
     * @throws NullPointerException 条目中的类或实例为 null
     * @throws IllegalStateException 本登记表已生产过管理器,或条目中的类已注册过
     */
    public RestRouteRegister registerRoute(Map<Class<?>, Object> routes) {
        for (Map.Entry<Class<?>, Object> entry : routes.entrySet()) {
            registerRoute(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * 指定 Bean 校验器:路由形参绑定为 bean 且注解要求校验时,由参数绑定器经该校验器执行校验;
     * 未指定时生产环节采用默认 Hibernate 引擎校验器
     *
     * @param beanValidator 校验器,不能为 null
     * @return 本登记表
     * @throws NullPointerException beanValidator 为 null
     * @throws IllegalStateException 本登记表已生产过管理器
     */
    public RestRouteRegister beanValidator(BeanValidator beanValidator) {
        checkNotProduced();
        this.beanValidator = Objects.requireNonNull(beanValidator, "beanValidator must not be null");
        return this;
    }

    /**
     * 按 class 剔除已注册的参数解析器,可同时剔除多个,null 元素无副作用
     *
     * @param toRemoveParsers 待剔除的解析器,按其 class 匹配表中的实例
     * @return 本登记表
     * @throws IllegalStateException 本登记表已生产过管理器
     */
    public RestRouteRegister removeParameterParser(ParameterParser... toRemoveParsers) {
        checkNotProduced();
        Iterator<ParameterParser> iterator = this.parsers.iterator();
        while (iterator.hasNext()) {
            ParameterParser parser = iterator.next();
            for (ParameterParser toRemoveParser : toRemoveParsers) {
                if (toRemoveParser != null && Objects.equals(toRemoveParser.getClass(), parser.getClass())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return this;
    }

    /**
     * 将指定解析器注册到解析器表开头(先于默认解析器被尝试)
     *
     * @param toRegisteredParser 待注册的解析器,不能为 null
     * @return 本登记表
     * @throws NullPointerException toRegisteredParser 为 null
     * @throws IllegalStateException 本登记表已生产过管理器,或同 class 的解析器已注册
     */
    public RestRouteRegister registerParameterParserFirst(ParameterParser toRegisteredParser) {
        validateBeforeRegisterParameterParser(toRegisteredParser);
        this.parsers.addFirst(toRegisteredParser);
        return this;
    }

    /**
     * 注册到解析器表末尾(默认解析器之后,最后被尝试)
     *
     * @param toRegisteredParser 待注册的解析器,不能为 null
     * @return 本登记表
     * @throws NullPointerException toRegisteredParser 为 null
     * @throws IllegalStateException 本登记表已生产过管理器,或同 class 的解析器已注册
     */
    public RestRouteRegister registerParameterParserLast(ParameterParser toRegisteredParser) {
        validateBeforeRegisterParameterParser(toRegisteredParser);
        this.parsers.addLast(toRegisteredParser);
        return this;
    }

    /**
     * 将指定解析器注册到目标解析器之前
     * 目标解析器按 class 定位,未注册时抛出 IllegalArgumentException
     *
     * @param targetParser 定位用的目标解析器,不能为 null
     * @param toRegisteredParser 待注册的解析器,不能为 null
     * @return 本登记表
     * @throws IllegalStateException 本登记表已生产过管理器,或同 class 的解析器已注册
     */
    public RestRouteRegister registerParameterParserBefore(ParameterParser targetParser, ParameterParser toRegisteredParser) {
        validateBeforeRegisterParameterParser(toRegisteredParser);
        int index = indexOfRegisteredParser(targetParser);
        this.parsers.add(index, toRegisteredParser);
        return this;
    }

    /**
     * 将指定解析器注册到目标解析器之后
     * 目标解析器按 class 定位,未注册时抛出 IllegalArgumentException
     *
     * @param targetParser 定位用的目标解析器,不能为 null
     * @param toRegisteredParser 待注册的解析器,不能为 null
     * @return 本登记表
     * @throws IllegalStateException 本登记表已生产过管理器,或同 class 的解析器已注册
     */
    public RestRouteRegister registerParameterParserAfter(ParameterParser targetParser, ParameterParser toRegisteredParser) {
        validateBeforeRegisterParameterParser(toRegisteredParser);
        int index = indexOfRegisteredParser(targetParser);
        this.parsers.add(index + 1, toRegisteredParser);
        return this;
    }

    /**
     * 用指定解析器原位替换目标解析器,其余解析器次序不动
     * 目标解析器按 class 定位,未注册时抛出 IllegalArgumentException
     *
     * @param targetParser 被替换的目标解析器,不能为 null
     * @param toRegisteredParser 替换进来的解析器,不能为 null
     * @return 本登记表
     * @throws IllegalStateException 本登记表已生产过管理器,或同 class 的解析器已注册
     */
    public RestRouteRegister registerParameterParser(ParameterParser targetParser, ParameterParser toRegisteredParser) {
        validateBeforeRegisterParameterParser(toRegisteredParser);
        int index = indexOfRegisteredParser(targetParser);
        this.parsers.remove(index);
        this.parsers.add(index, toRegisteredParser);
        return this;
    }

    /**
     * 装配路由表并生产管理器:逐控制器解析元信息,按 url 聚合为路由处理器后以 url 为模式
     * 登记进基数树,连同参数解析器表与 Bean 校验器一并装入只读快照;json 序列化器随各路由处理器
     * 构造注入,供其参数绑定器绑定 bean 时取用
     *
     * <p>同一 url 上的多个请求方式合进一个处理器;某段路径含连续斜杠、同路径同请求方式重复、
     * 或模式与既有注册面歧义,都在本时点直接失败。生产之后的登记表不再接受变更。
     *
     * @param jsonSerializer json 序列化器,不能为 null;随各路由处理器构造注入供参数绑定使用,与装配方共用同一实例
     * @return 只读路由表对应的路由器管理器,未注册任何路由时其对所有请求都按路由未匹配处理
     * @throws NullPointerException     jsonSerializer 为 null
     * @throws IllegalStateException    本登记表已生产过管理器
     * @throws IllegalArgumentException 路径非法、同请求方式在同一路径上重复,或模式与既有注册面歧义
     */
    @Override
    public RouteHandlerManager newRouteHandlerManager(JsonSerializer jsonSerializer) {
        Objects.requireNonNull(jsonSerializer, "jsonSerializer must not be null");
        if (!produced.compareAndSet(false, true)) {
            throw new IllegalStateException(PRODUCED_MESSAGE);
        }
        Map<String, List<RouteHandlerMetaInfo>> routeMetaInfoMap = routeInstanceMap.entrySet().stream()
                .flatMap(entry -> {
                    Class<?> targetClass = entry.getKey();
                    Object instance = entry.getValue();
                    return RouteHandlerMetaInfoHelper.parse(this.baseUrl, instance, targetClass).stream();
                })
                .collect(Collectors.groupingBy(RouteHandlerMetaInfo::url));
        // 未指定校验器时采用默认校验器,随各路由处理器构造传入
        BeanValidator validator = this.beanValidator != null ? this.beanValidator : DEFAULT_BEAN_VALIDATOR;
        // 解析器表与校验器取生产时刻的快照,装配出的路由处理器不持有可变登记面
        List<ParameterParser> registeredParsers = List.copyOf(this.parsers);
        // 构建基数树
        RestRouteTree<RouteHandler> restRouteTree = new RestRouteTree<>();
        routeMetaInfoMap.forEach((url, metaInfos) -> {
            RouteHandler routeHandler = new RouteHandler(metaInfos, registeredParsers, validator, jsonSerializer);
            restRouteTree.put(routeHandler.path(), routeHandler);
        });
        return new RouterManager(restRouteTree.toReadView());
    }

    /**
     * 按 class 查找已注册解析器的下标
     *
     * @return 目标解析器在表中的位置
     * @throws NullPointerException     targetParser 为 null
     * @throws IllegalArgumentException targetParser 的 class 未注册
     */
    private int indexOfRegisteredParser(ParameterParser targetParser) {
        Objects.requireNonNull(targetParser, "The target parser must not be null");
        for (int index = 0; index < this.parsers.size(); index++) {
            if (Objects.equals(this.parsers.get(index).getClass(), targetParser.getClass())) {
                return index;
            }
        }
        throw new IllegalArgumentException("The target parser is not registered: " + targetParser.getClass());
    }

    private void validateBeforeRegisterParameterParser(ParameterParser toRegisteredParser) {
        checkNotProduced();
        Objects.requireNonNull(toRegisteredParser, "ParameterParser must not be null");
        boolean exist = this.parsers.stream().anyMatch(parser -> Objects.equals(
                toRegisteredParser.getClass(), parser.getClass())
        );
        if (exist) {
            throw new IllegalStateException(toRegisteredParser.getClass() + " is already registered");
        }
    }

    /**
     * 变更入口的门闩检查:登记表一经生产即冻结
     *
     * @throws IllegalStateException 本登记表已生产过管理器
     */
    private void checkNotProduced() {
        if (produced.get()) {
            throw new IllegalStateException(PRODUCED_MESSAGE);
        }
    }

    /**
     * rest 风格的路由处理器管理器：登记表在生产时刻装配出的只读快照。
     *
     * <p>请求处理按路径逐段匹配基数树，路径与查询参数解码后装入非请求体参数承载随处理器调用传递；
     * 路径未命中、或命中路径但请求方式无对应处理器，一律按路由不匹配错误码抛出，由异常处理器转 404。
     */
    private static final class RouterManager implements RouteHandlerManager {

        /**
         * 路由表只读视图，生产期装入后不再变动
         */
        private final RouteTree<RouteHandler> routeHandlerRadixTree;

        private RouterManager(RouteTree<RouteHandler> routeHandlerRadixTree) {
            this.routeHandlerRadixTree = routeHandlerRadixTree;
        }

        /**
         * 处理请求:按请求路径匹配路由处理器并执行
         *
         * <p>匹配路径取上下文消息地址的仅路径形态（查询段与片段段不入匹配）;命中后路径参数按
         * 上下文缺省字符集解码,查询参数直接取消息地址按同一字符集解码并按名聚合的结果,
         * 两者一并装入非请求体参数承载随处理器调用传递,由参数绑定器取用,处理器按请求方式分发。
         *
         * @param context 请求上下文
         * @return 目标方法的返回值
         * @throws RouteHandlerException 路由未匹配(含路径未命中与请求方式无对应处理器),错误码为
         *                              RouterNotMatch;路径或查询参数的百分号转义畸形在装配点包装,
         *                              错误码为 ParamDecodeError(400);目标方法抛出的非运行时异常
         *                              包装后错误码为 RouterInvokeError,运行时异常原样透传
         */
        @Override
        public Object handle(HttpContext context) {
            HttpContext.Message message = context.message();
            HttpContext.URL url = message.url();
            String path = url.url(true);
            RouteTree.Result<RouteHandler> matched = this.routeHandlerRadixTree.match(path);
            if (matched == null) {
                // 与注解路由同口径:未匹配按路由不匹配错误码抛出,由异常处理器转 404
                throw new RouteHandlerException(
                        String.format("router not found: %s %s", message.method(), url.url(false)),
                        ErrorCode.RouterNotMatch);
            }
            // 路径与查询参数都是 URI 侧数据,装配期解码就位后随处理器调用传递,不进请求上下文;
            // 匹配先于解码:未命中路径的 URI 不做解码劳动
            Map<String, String> pathParams;
            try {
                pathParams = decodePathParams(matched.params(), context.defaultCharset());
            } catch (IllegalArgumentException e) {
                throw new RouteHandlerException(e, ErrorCode.ParamDecodeError);
            }
            Map<String, List<String>> queryParams;
            try {
                queryParams = url.queryParams();
            } catch (IllegalArgumentException e) {
                throw new RouteHandlerException(e, ErrorCode.ParamDecodeError);
            }
            RequestParameter requestParameter = new RequestParameter(pathParams, queryParams);
            try {
                return matched.value().handle(context, requestParameter);
            } catch (Throwable e) {
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RouteHandlerException(e, ErrorCode.RouterInvokeError);
            }
        }

        /**
         * 逐个解码路径参数值,解码字符集取上下文的缺省字符集（URL 百分号编码不受请求头声明影响）
         *
         * @param params  命中模式上的参数绑定,键为参数名
         * @param charset 解码字符集
         * @return 解码后的参数映射,与入参不同实例,原映射不改动
         */
        private static Map<String, String> decodePathParams(Map<String, String> params, Charset charset) {
            Map<String, String> decoded = new HashMap<>(params.size(), 1);
            params.forEach((name, value) -> decoded.put(name, URLDecoder.decode(value, charset)));
            return decoded;
        }
    }

}
