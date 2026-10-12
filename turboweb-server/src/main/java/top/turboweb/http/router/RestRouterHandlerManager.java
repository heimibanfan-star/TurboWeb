package top.turboweb.http.router;

import io.netty.handler.codec.http.FullHttpRequest;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.middleware.router.info.autobind.*;
import top.turboweb.struct.radix.RestRouteTree;
import top.turboweb.struct.radix.RouteTree;

import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * rest风格的路由处理器管理器
 *
 * <p>路由表由 {@link Builder} 在装配期构建:注解解析出的元信息按 url 聚合成路由处理器,
 * 再以 url 为模式登记进基数树,树对管理器只读。
 *
 * <p>请求处理按路径逐段匹配基数树,路径参数从命中模式上取出并解码注入上下文;
 * 路径未命中、或命中路径但请求方式无对应处理器,一律按路由不匹配错误码抛出,由异常处理器转 404。
 */
public class RestRouterHandlerManager implements RouteHandlerManager {

    private final RouteTree<RouteHandler> routeHandlerRadixTree;

    private RestRouterHandlerManager(RouteTree<RouteHandler> routeHandlerRadixTree) {
        this.routeHandlerRadixTree = routeHandlerRadixTree;
    }

    /**
     * 处理请求:按请求路径匹配路由处理器并执行
     *
     * <p>查询串不属路径,匹配前先剥离;命中模式上的路径参数按请求字符集解码后注入上下文,
     * 再交由处理器按请求方式分发。
     *
     * @param context 请求上下文
     * @return 目标方法的返回值
     * @throws RouteHandlerException 路由未匹配(含路径未命中与请求方式无对应处理器),错误码为
     *                              ROUTER_NOT_MATCH;目标方法抛出的非运行时异常包装后错误码为
     *                              ROUTER_INVOKE_ERROR,运行时异常原样透传
     */
    @Override
    public Object handle(HttpContext context) {
        FullHttpRequest request = context.getRequest();
        String path = request.uri();
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }
        RouteTree.Result<RouteHandler> matched = this.routeHandlerRadixTree.match(path);
        if (matched == null) {
            // 与注解路由同口径:未匹配按路由不匹配错误码抛出,由异常处理器转 404
            throw new RouteHandlerException(
                    String.format("router not found: %s %s", request.method().name(), request.uri()),
                    RouteHandlerException.ROUTER_NOT_MATCH);
        }
        context.injectPathParam(decodePathParams(matched.params()));
        try {
            return matched.value().handle(context);
        } catch (Throwable e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RouteHandlerException(e, RouteHandlerException.ROUTER_INVOKE_ERROR);
        }
    }

    /**
     * 逐个解码路径参数值,解码字符集取请求字符集
     *
     * @param params 命中模式上的参数绑定,键为参数名
     * @return 解码后的参数映射,与入参不同实例,原映射不改动
     */
    private static Map<String, String> decodePathParams(Map<String, String> params) {
        Map<String, String> decoded = new HashMap<>(params.size(), 1);
        params.forEach((name, value) -> decoded.put(name, URLDecoder.decode(value, GlobalConfig.getRequestCharset())));
        return decoded;
    }

    public static class Builder {

        private static final String SPLIT_PATH = "/";
        private final String baseUrl;
        private final Map<Class<?>, Object> routeInstanceMap = new HashMap<>();
        private final LinkedList<ParameterInfoParser> parsers = new LinkedList<>();

        /**
         * 构建路由注册器
         *
         * <p>前缀只裁冗余的尾部斜杠:连续斜杠会被路径合并判为非法路径,而尾斜杠属于可规整的冗余。
         * 缺前导斜杠与根路径形态不在此处理,统一由合并环节的规整负责。
         *
         * @param baseUrl 路由前缀,null 与空串视为无前缀;含连续斜杠时于 build 阶段按非法路径拒绝
         */
        public Builder(String baseUrl) {
            // 对基础地址进行预处理
            baseUrl = Optional.ofNullable(baseUrl).orElse("");
            while (baseUrl.endsWith(SPLIT_PATH)) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            this.baseUrl = baseUrl;
            // 注册默认的参数解析器
            parsers.add(new InternTypeParamInfoParser());
            parsers.add(new RestParameterInfoParser());
            parsers.add(new QueryParameterInfoParser());
            parsers.add(new UploadParameterInfoParser());
            parsers.add(new QueryModelParameterInfoParser());
            parsers.add(new FormModelParameterInfoParser());
            parsers.add(new JsonModelParameterInfoParser());
        }

        /**
         * 构建无前缀的路由注册器
         */
        public Builder() {
            this(null);
        }

        /**
         * 注册路由:注解按显式给出的类扫描,调用落在给定实例上
         *
         * @param targetClass 注解扫描的类,不能为 null
         * @param instance 路由处理器实例,不能为 null
         * @return 本构建器
         * @throws NullPointerException targetClass 或 instance 为 null
         * @throws IllegalArgumentException targetClass 已注册过
         */
        public Builder registerRoute(Class<?> targetClass, Object instance) {
            Objects.requireNonNull(targetClass, "targetClass must not be null");
            Objects.requireNonNull(instance, "routeInstance must not be null");
            if (this.routeInstanceMap.containsKey(targetClass)) {
                throw new IllegalArgumentException(targetClass + " is already registered");
            }
            this.routeInstanceMap.put(targetClass, instance);
            return this;
        }

        /**
         * 注册路由实例,注解扫描取实例运行时的类
         *
         * @param instance 路由处理器实例,不能为 null
         * @return 本构建器
         * @throws NullPointerException instance 为 null
         * @throws IllegalArgumentException 实例的类已注册过
         */
        public Builder registerRoute(Object instance) {
            Objects.requireNonNull(instance, "routeInstance must not be null");
            return registerRoute(instance.getClass(), instance);
        }

        /**
         * 批量注册路由实例,逐个按各自的运行时类登记
         *
         * @param instances 路由处理器实例列表,null 按无注册处理
         * @return 本构建器
         * @throws NullPointerException 列表中出现 null 元素
         * @throws IllegalArgumentException 列表中出现同类实例
         */
        public Builder registerRoute(List<Object> instances) {
            for (Object instance : Optional.ofNullable(instances).orElse(List.of())) {
                registerRoute(instance);
            }
            return this;
        }

        /**
         * 批量注册路由表,每个条目按类与实例成对登记
         *
         * @param routes 类到实例的映射
         * @return 本构建器
         * @throws NullPointerException 条目中的类或实例为 null
         * @throws IllegalArgumentException 条目中的类已注册过
         */
        public Builder registerRoute(Map<Class<?>, Object> routes) {
            for (Map.Entry<Class<?>, Object> entry : routes.entrySet()) {
                registerRoute(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /**
         * 按 class 剔除已注册的参数解析器,可同时剔除多个,null 元素无副作用
         *
         * @param toRemoveParsers 待剔除的解析器,按其 class 匹配表中的实例
         * @return 本构建器
         */
        public Builder removeParameterParser(ParameterInfoParser... toRemoveParsers) {
            Iterator<ParameterInfoParser> iterator = this.parsers.iterator();
            while (iterator.hasNext()) {
                ParameterInfoParser parser = iterator.next();
                for (ParameterInfoParser toRemoveParser : toRemoveParsers) {
                    if (toRemoveParser != null && Objects.equals(toRemoveParser.getClass(), parser.getClass())) {
                        iterator.remove();
                        break;
                    }
                }
            }
            return this;
        }

        /**
         * 将指定解析器注册到解析器列表开头(先于默认解析器被尝试)
         *
         * @param toRegisteredParser 待注册的解析器,不能为 null
         * @return 本构建器
         * @throws NullPointerException toRegisteredParser 为 null
         * @throws IllegalArgumentException 同 class 的解析器已注册
         */
        public Builder registerParameterParserFirst(ParameterInfoParser toRegisteredParser) {
            validateBeforeRegisterParameterParser(toRegisteredParser);
            this.parsers.addFirst(toRegisteredParser);
            return this;
        }

        /**
         * 注册到解析器列表末尾(默认解析器之后,最后被尝试)
         *
         * @param toRegisteredParser 待注册的解析器,不能为 null
         * @return 本构建器
         * @throws NullPointerException toRegisteredParser 为 null
         * @throws IllegalArgumentException 同 class 的解析器已注册
         */
        public Builder registerParameterParserLast(ParameterInfoParser toRegisteredParser) {
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
         * @return 本构建器
         */
        public Builder registerParameterParserBefore(ParameterInfoParser targetParser, ParameterInfoParser toRegisteredParser) {
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
         * @return 本构建器
         */
        public Builder registerParameterParserAfter(ParameterInfoParser targetParser, ParameterInfoParser toRegisteredParser) {
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
         * @return 本构建器
         */
        public Builder registerParameterParser(ParameterInfoParser targetParser, ParameterInfoParser toRegisteredParser) {
            validateBeforeRegisterParameterParser(toRegisteredParser);
            int index = indexOfRegisteredParser(targetParser);
            this.parsers.remove(index);
            this.parsers.add(index, toRegisteredParser);
            return this;
        }

        /**
         * 按 class 查找已注册解析器的下标
         *
         * @return 目标解析器在列表中的位置
         * @throws NullPointerException     targetParser 为 null
         * @throws IllegalArgumentException targetParser 的 class 未注册
         */
        private int indexOfRegisteredParser(ParameterInfoParser targetParser) {
            Objects.requireNonNull(targetParser, "目标解析器不能为空");
            for (int index = 0; index < this.parsers.size(); index++) {
                if (Objects.equals(this.parsers.get(index).getClass(), targetParser.getClass())) {
                    return index;
                }
            }
            throw new IllegalArgumentException("目标解析器未注册: " + targetParser.getClass());
        }

        private void validateBeforeRegisterParameterParser(ParameterInfoParser toRegisteredParser) {
            Objects.requireNonNull(toRegisteredParser, "ParameterInfoParser must not be null");
            boolean exist = this.parsers.stream().anyMatch(parser -> Objects.equals(
                    toRegisteredParser.getClass(), parser.getClass())
            );
            if (exist) {
                throw new IllegalArgumentException(toRegisteredParser.getClass() + " is already registered");
            }
        }

        /**
         * 装配路由表:逐控制器解析元信息,按 url 聚合为路由处理器后以 url 为模式登记进基数树
         *
         * <p>同一 url 上的多个请求方式合进一个处理器;某段路径含连续斜杠、同路径同请求方式重复、
         * 或模式与既有注册面歧义,都在构建期直接失败。构建后的树对管理器只读。
         *
         * @return 只读路由表对应的路由器管理器,未注册任何路由时其对所有请求都按路由未匹配处理
         * @throws IllegalArgumentException 路径非法、同请求方式在同一路径上重复,或模式与既有注册面歧义
         */
        public RouteHandlerManager build() {
            Map<String, List<RouteHandlerMetaInfo>> routeMetaInfoMap = routeInstanceMap.entrySet().stream()
                    .flatMap(entry -> {
                        Class<?> targetClass = entry.getKey();
                        Object instance = entry.getValue();
                        return RouteHandlerMetaInfoHelper.parse(this.baseUrl, instance, targetClass).stream();
                    })
                    .collect(Collectors.groupingBy(RouteHandlerMetaInfo::url));
            // 构建基数树
            RestRouteTree<RouteHandler> restRouteTree = new RestRouteTree<>();
            routeMetaInfoMap.forEach((url, metaInfos) -> {
                RouteHandler routeHandler = new RouteHandler(metaInfos, parsers);
                restRouteTree.put(routeHandler.path(), routeHandler);
            });
            return new RestRouterHandlerManager(restRouteTree.toReadView());
        }

    }

}
