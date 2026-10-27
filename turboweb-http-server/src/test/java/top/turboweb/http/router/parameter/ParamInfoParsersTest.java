package top.turboweb.http.router.parameter;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.rest.core.HttpContent;
import top.turboweb.rest.core.HttpRequestMessage;
import top.turboweb.rest.core.HttpRequestMethod;
import top.turboweb.rest.core.HttpUrl;
import top.turboweb.rest.core.MultipartFile;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpFormContent;
import top.turboweb.http.context.HttpJsonContent;
import top.turboweb.http.testkit.Requests;
import top.turboweb.http.connection.HttpConnection;
import top.turboweb.http.core.connect.InternalHttpConnection;
import top.turboweb.rest.core.ContentType;
import top.turboweb.http.cookie.HttpCookie;
import top.turboweb.http.cookie.HttpCookies;
import top.turboweb.http.core.response.InternalSseEmitter;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.session.HttpSession;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.http.exception.SseBufferException;
import top.turboweb.rest.annotation.binder.*;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.channels.Channel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * autoBind 九个参数解析器各自的解析逻辑（spec §5：autoBind 解析器认领与绑定契约）。
 *
 * <p>分派那一侧（谁在前、谁先认领）归 Task 9；本类只钉"每个 parser 对单个
 * {@link Parameter} 怎么认领、认领后怎么绑定"。
 *
 * <p>L1 说明：`doParse` 只吃反射参数，无需上下文；`bindParameter` 需要
 * {@link HttpContext}，而它是接口 —— 用 {@link Proxy} 手写记录型替身（spec §2.2 排除 Mockito），
 * 不起 socket、不碰线程。表单与 JSON 通道的取数认内置内容具体类，相应内容以轻量请求夹具
 * 真解析产出（构造期即完成解析，请求随解析返回释放）；其余通道仍只投喂内存对象。
 * bean 绑定通道由绑定器直调传入的 json 序列化器完成，本类用记录型序列化器钉调用与入料，
 * 折叠与反序列化语义以真 Jackson 序列化器端到端钉住。
 */
class ParamInfoParsersTest {

    /** 参数夹具：每个方法承载一种"注解 + 类型"形态，测试经反射取其唯一的 {@link Parameter}。 */
    @SuppressWarnings("unused")
    private static final class Params {

        // —— @QueryParam ——
        void queryString(@QueryParam("q") String v) {}
        void queryNonNull(@QueryParam(value = "q", nonNull = true) String v) {}
        void queryInt(@QueryParam("n") int v) {}
        void queryList(@QueryParam("q") List<Integer> v) {}
        void querySet(@QueryParam("q") Set<String> v) {}
        void queryUnsupportedCollection(@QueryParam("q") Collection<String> v) {}
        void queryUnsupportedElement(@QueryParam("q") Map<String, String> v) {}

        // —— 内部类型（无注解，按声明类型认领） ——
        void internContext(HttpContext v) {}
        void internSession(HttpSession v) {}
        void internCookies(HttpCookies v) {}
        void internSseResponse(SseResponse v) {}
        void internSseEmitter(SseEmitter v) {}
        void internConnectSession(HttpConnection v) {}
        void internChannel(Channel v) {}
        void internUnsupported(String v) {}

        // —— @FormParam 文件形态 ——
        void uploadFile(@FormParam("f") MultipartFile v) {}
        void uploadObject(@FormParam("f") Object v) {}
        void uploadList(@FormParam("f") List<MultipartFile> v) {}
        void uploadSet(@FormParam("f") Set<MultipartFile> v) {}
        void uploadListWithoutAnnotation(List<MultipartFile> v) {}
        void uploadStringList(@FormParam("f") List<String> v) {}
        void uploadRawCollection(@FormParam("f") Collection<MultipartFile> v) {}
        void uploadWildcardList(@FormParam("f") List<?> v) {}
        void uploadFileWithoutAnnotation(MultipartFile v) {}
        void uploadObjectWithoutAnnotation(Object v) {}

        // —— Bean ——
        void beanJson(@BeanJson Bean v) {}
        void beanJsonValid(@BeanJson(true) Bean v) {}
        void beanJsonGroups(@BeanJson(value = true, groups = GroupA.class) Bean v) {}
        void beanJsonPlain(Bean v) {}
        void beanJsonBound(@BeanJson Bound v) {}

        void beanForm(@BeanForm Bean v) {}
        void beanFormValid(@BeanForm(true) Bean v) {}
        void beanFormGroups(@BeanForm(value = true, groups = GroupA.class) Bean v) {}
        void beanFormPlain(Bean v) {}
        void beanFormBound(@BeanForm Bound v) {}

        void beanQuery(@BeanQuery Bean v) {}
        void beanQueryValid(@BeanQuery(true) Bean v) {}
        void beanQueryGroups(@BeanQuery(value = true, groups = GroupA.class) Bean v) {}
        void beanQueryPlain(Bean v) {}
        void beanQueryBound(@BeanQuery Bound v) {}

        // —— @PathParam ——
        void pathInt(@PathParam("id") int v) {}
        void pathUnsupported(@PathParam("id") Map<String, String> v) {}
        void pathPlain(int v) {}

        // —— @CookieParam ——
        void cookieString(@CookieParam("c") String v) {}
        void cookieInt(@CookieParam("n") int v) {}
        void cookieUnsupported(@CookieParam("c") Map<String, String> v) {}
        void cookiePlain(String v) {}

        // —— @FormParam ——
        void formString(@FormParam("f") String v) {}
        void formInt(@FormParam("n") int v) {}
        void formUnsupported(@FormParam("f") Map<String, String> v) {}
        void formPlain(String v) {}

        // —— @HeaderParam ——
        void headerString(@HeaderParam("h") String v) {}
        void headerInt(@HeaderParam("n") int v) {}
        void headerUnsupported(@HeaderParam("h") Map<String, String> v) {}
        void headerPlain(String v) {}

        void noAnnotation(String v) {}
    }

    private static final class Bean {}

    private static final class GroupA {}

    /** 真 Jackson 绑定语义的 bean 夹具：单值属性与多值属性各一 */
    public static class Bound {
        private String name;
        private List<String> tag;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTag() {
            return tag;
        }

        public void setTag(List<String> tag) {
            this.tag = tag;
        }
    }

    /** 按夹具方法名取唯一的 {@link Parameter}；方法不存在直接抛，避免静默过测。 */
    private static Parameter parameter(String methodName) {
        return Arrays.stream(Params.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .filter(m -> m.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("夹具方法不存在: " + methodName))
                .getParameters()[0];
    }

    /**
     * 记录型 {@link HttpContext} 替身：按方法名返回预设值，并留下最后一次调用的实参。
     * "content" 为内容投喂口径——上下文的取数经 {@code message().content()}，故该项注册时
     * 包装为只应答内容通道的消息替身挂到 message() 下。
     */
    private static final class Stub {
        private final Map<String, Object> returns = new HashMap<>();
        private final Map<String, Object[]> lastArgs = new HashMap<>();
        private final HttpContext proxy = (HttpContext) Proxy.newProxyInstance(
                HttpContext.class.getClassLoader(),
                new Class<?>[]{HttpContext.class},
                (p, m, a) -> {
                    if (m.getDeclaringClass() == Object.class) {
                        return switch (m.getName()) {
                            case "equals" -> Boolean.valueOf(p == a[0]);
                            case "hashCode" -> Integer.valueOf(System.identityHashCode(p));
                            default -> "StubHttpContext";
                        };
                    }
                    lastArgs.put(m.getName(), a == null ? new Object[0] : a);
                    return returns.get(m.getName());
                });

        Stub returning(String method, Object value) {
            if ("content".equals(method)) {
                returns.put("message", new ContentFeedingMessage((HttpContent) value));
            } else {
                returns.put(method, value);
            }
            return this;
        }

        HttpContext ctx() {
            return proxy;
        }

        Object[] lastArgs(String method) {
            Object[] args = lastArgs.get(method);
            if (args == null) {
                throw new AssertionError("未调用 HttpContext." + method);
            }
            return args;
        }
    }

    /** 返回 null 的哨兵代理，用于占位那些只被"取回并断言同一性"的接口对象。 */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                HttpContext.class.getClassLoader(),
                new Class<?>[]{type},
                (p, m, a) -> {
                    if (m.getDeclaringClass() == Object.class) {
                        return switch (m.getName()) {
                            case "equals" -> Boolean.valueOf(p == a[0]);
                            case "hashCode" -> Integer.valueOf(System.identityHashCode(p));
                            default -> "proxy:" + type.getSimpleName();
                        };
                    }
                    return null;
                });
    }

    /** 空操作校验器占位，供不经校验通道的绑定器使用。 */
    private static final BeanValidator NOOP = new BeanValidator() {
        @Override
        public void validate(Object bean) {
        }

        @Override
        public void validate(Object bean, Class<?>... groups) {
        }

        @Override
        public void close() {
        }
    };

    /**
     * 记录型 {@link JsonSerializer} 替身：只放行 mapToBean 与 jsonToBean 两枚 bean 绑定通道，
     * 返回预置的绑定对象并留下最近一次调用与实参；其余序列化方法与绑定无关，误调即响。
     */
    private static final class RecordingSerializer implements JsonSerializer {
        private String lastMethod;
        private Object[] lastArgs;
        private Object bound = new Object();

        RecordingSerializer bound(Object value) {
            this.bound = value;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
            lastMethod = "mapToBean";
            lastArgs = new Object[]{map, beanClass};
            return (T) bound;
        }

        @Override
        public <T> T jsonToBean(String json, Class<T> beanClass) {
            lastMethod = "jsonToBean";
            lastArgs = new Object[]{json, beanClass};
            return beanClass.cast(bound);
        }

        @Override
        public Map<?, ?> beanToMap(Object bean) {
            throw unsupported("beanToMap");
        }

        @Override
        public String beanToJson(Object bean) {
            throw unsupported("beanToJson");
        }

        @Override
        public Map<?, ?> jsonToMap(String json) {
            throw unsupported("jsonToMap");
        }

        @Override
        public <T> List<T> jsonToList(String json, Class<T> beanClass) {
            throw unsupported("jsonToList");
        }

        @Override
        public <T> T deepinClone(T bean) {
            throw unsupported("deepinClone");
        }

        @Override
        public <T> T jsonUpdateBean(String json, T bean) {
            throw unsupported("jsonUpdateBean");
        }

        private static UnsupportedOperationException unsupported(String name) {
            return new UnsupportedOperationException("参数绑定不经序列化器的 " + name + " 方法");
        }
    }

    /** 不经 bean 绑定通道的绑定器所用序列化器占位。 */
    private static final RecordingSerializer NOOP_SERIALIZER = new RecordingSerializer();

    /** 真 Jackson 序列化器：绑定器侧折叠与反序列化语义的端到端钉住所用。 */
    private static final JacksonJsonSerializer JACKSON = new JacksonJsonSerializer();

    /** 空承载夹具:路径与查询都无参数的装配结果,表达"缺名"入料 */
    private static final RequestParameter NO_PARAMS = new RequestParameter(Map.of(), Map.of());

    /** 内置表单内容工厂：以 urlencoded 请求构造 {@link HttpFormContent}，向绑定器投喂真实字段归档。 */
    private static HttpFormContent formContentOf(String urlencoded) {
        FullHttpRequest request = Requests.postForm("/fixture", urlencoded);
        try {
            return new HttpFormContent(request, StandardCharsets.UTF_8);
        } finally {
            Requests.release(request);
        }
    }

    /** 内置 multipart 表单内容工厂：每个三元组（字段名、文件名、内容）编码为一个文件段，归档进 formFiles。 */
    private static HttpFormContent multipartContentOf(String[]... files) {
        StringBuilder body = new StringBuilder();
        for (String[] entry : files) {
            body.append("--B\r\nContent-Disposition: form-data; name=\"").append(entry[0])
                    .append("\"; filename=\"").append(entry[1])
                    .append("\"\r\nContent-Type: text/plain\r\n\r\n").append(entry[2]).append("\r\n");
        }
        body.append("--B--\r\n");
        FullHttpRequest request = Requests.raw(HttpMethod.POST, "/fixture",
                "multipart/form-data; boundary=B", body.toString(), StandardCharsets.UTF_8);
        try {
            return new HttpFormContent(request, StandardCharsets.UTF_8);
        } finally {
            Requests.release(request);
        }
    }

    /** 内置 JSON 内容工厂：以 JSON 请求构造 {@link HttpJsonContent}，向绑定器投喂真实体字符串。 */
    private static HttpJsonContent jsonContentOf(String jsonBody) {
        FullHttpRequest request = Requests.postJson("/fixture", jsonBody);
        try {
            return new HttpJsonContent(request, StandardCharsets.UTF_8);
        } finally {
            Requests.release(request);
        }
    }

    /** 无通道内容夹具：只实现 Content 基础契约，供绑定器拒绑用例。 */
    private record PlainContentFixture() implements HttpContent {

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
    }

    /** Cookie 集合夹具：仅 getCookie 读取通道向绑定器投喂指定名称归档，其余通道误调即响。 */
    private record CookiesFixture(Map<String, String> values) implements HttpCookies {

        @Override
        public String getCookie(String key) {
            return values.get(key);
        }

        @Override
        public void setCookie(HttpCookie cookie) {
            throw offChannel("setCookie");
        }

        @Override
        public void setCookie(String key, String value) {
            throw offChannel("setCookie");
        }

        @Override
        public void setCookie(String key, String value, Consumer<HttpCookie> consumer) {
            throw offChannel("setCookie");
        }

        @Override
        public Map<String, String> getCookies() {
            throw offChannel("getCookies");
        }

        @Override
        public void removeCookie(String key) {
            throw offChannel("removeCookie");
        }

        @Override
        public void clearToWriteCookies() {
            throw offChannel("clearToWriteCookies");
        }

        @Override
        public void clearAll() {
            throw offChannel("clearAll");
        }

        @Override
        public List<String> toSetCookies(Charset charset) {
            throw offChannel("toSetCookies");
        }

        private static UnsupportedOperationException offChannel(String name) {
            return new UnsupportedOperationException("参数绑定不经 Cookie 的 " + name + " 通道");
        }
    }

    /** 请求消息夹具：只承接请求头通道，方式、地址与内容通道不经绑定，误调即响。 */
    private record MessageFixture(Map<String, String> headers) implements HttpRequestMessage {

        @Override
        public HttpRequestMethod method() {
            throw offChannel("method");
        }

        @Override
        public HttpUrl url() {
            throw offChannel("url");
        }

        @Override
        public HttpContent content() {
            throw offChannel("content");
        }

        @Override
        public String header(String name) {
            return headers.get(name);
        }

        @Override
        public List<String> headerValues(String name) {
            String value = headers.get(name);
            return value == null ? List.of() : List.of(value);
        }

        @Override
        public boolean containsHeader(String name) {
            return headers.containsKey(name);
        }

        @Override
        public Set<String> headerNames() {
            return headers.keySet();
        }

        @Override
        public int headerSize() {
            return headers.size();
        }

        @Override
        public boolean headerIsEmpty() {
            return headers.isEmpty();
        }

        private static UnsupportedOperationException offChannel(String name) {
            return new UnsupportedOperationException("参数绑定不经消息的 " + name + " 通道");
        }
    }

    /** 内容投喂消息替身：只应答内容通道，其余消息通道不经绑定、误调即响。 */
    private record ContentFeedingMessage(HttpContent content) implements HttpRequestMessage {

        @Override
        public HttpContent content() {
            return content;
        }

        @Override
        public HttpRequestMethod method() {
            throw offChannel("method");
        }

        @Override
        public HttpUrl url() {
            throw offChannel("url");
        }

        @Override
        public String header(String name) {
            throw offChannel("header");
        }

        @Override
        public List<String> headerValues(String name) {
            throw offChannel("headerValues");
        }

        @Override
        public boolean containsHeader(String name) {
            throw offChannel("containsHeader");
        }

        @Override
        public Set<String> headerNames() {
            throw offChannel("headerNames");
        }

        @Override
        public int headerSize() {
            throw offChannel("headerSize");
        }

        @Override
        public boolean headerIsEmpty() {
            throw offChannel("headerIsEmpty");
        }

        private static UnsupportedOperationException offChannel(String name) {
            return new UnsupportedOperationException("参数绑定不经消息的 " + name + " 通道");
        }
    }

    /** 记录型 {@link BeanValidator} 替身：记录最近一次校验的 bean 与分组（单参重载记 null）及调用次数。 */
    private static final class RecordingValidator implements BeanValidator {
        private int count;
        private Object bean;
        private Class<?>[] groups;

        @Override
        public void validate(Object obj) {
            count++;
            bean = obj;
            groups = null;
        }

        @Override
        public void validate(Object obj, Class<?>... gs) {
            count++;
            bean = obj;
            groups = gs;
        }

        @Override
        public void close() {
        }
    }

    // ---------- QueryParameterParser ----------

    @Test
    void queryWithoutAnnotationIsIgnored() {
        assertNull(new QueryParameterParser().parse(parameter("noAnnotation")));
    }

    @Test
    void queryStringBindsConvertedValue() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryString"));
        assertNotNull(b);
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("hello")));
        assertEquals("hello", b.bindParameter(new Stub().ctx(), params, NOOP, NOOP_SERIALIZER), "注解的 value 即查询参数名:承载对象 q 键首值");
        assertNull(b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "缺名取不到值,String 转换器 null 直通");
    }

    @Test
    void queryUnsupportedElementTypeYieldsNoBinder() {
        assertNull(new QueryParameterParser().parse(parameter("queryUnsupportedElement")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    @Test
    void queryUnsupportedCollectionTypeThrows() {
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> new QueryParameterParser().parse(parameter("queryUnsupportedCollection")));
        assertEquals("not support type of collection:java.util.Collection", ex.getMessage());
    }

    @Test
    void queryListBindsEveryValue() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryList"));
        assertNotNull(b);
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("1", "2")));
        assertEquals(List.of(1, 2), b.bindParameter(new Stub().ctx(), params, NOOP, NOOP_SERIALIZER), "List<Integer> 逐元素转换");
        assertEquals(List.of(), b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "缺名无多值");
    }

    @Test
    void queryListWithoutValuesIsEmptyList() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryList"));
        assertEquals(List.of(), b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void querySetDeduplicates() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("querySet"));
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("a", "b", "a")));
        assertEquals(Set.of("a", "b"), b.bindParameter(new Stub().ctx(), params, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void querySetWithoutValuesIsEmptySet() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("querySet"));
        assertEquals(Set.of(), b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void queryNonNullRejectsMissingValue() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryNonNull"));
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("校验失败:[q must not be null]", ex.getMessage());
        assertEquals(ErrorCode.MissingParamError, ex.getCode());
        assertEquals(10005, ex.getCode().getValue(), "数值错误码钉住");
    }

    @Test
    void queryNonNullPassesWhenValuePresent() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryNonNull"));
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("v")));
        assertEquals("v", b.bindParameter(new Stub().ctx(), params, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void queryPrimitiveIntDefaultsToZeroWhenMissing() {
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryInt"));
        assertEquals(0, b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "int 转换器对 null 返回 0（与 Integer 的 null 区分）");
    }

    // ---------- InternTypeParameterParser ----------

    @Test
    void internBindsEachSupportedInternalType() {
        Stub stub = new Stub();
        HttpContext ctx = stub.ctx();
        InternTypeParameterParser p = new InternTypeParameterParser();

        assertSame(ctx, p.parse(parameter("internContext")).bindParameter(ctx, NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "HttpContext 参数注入的就是当前上下文");

        HttpSession session = proxy(HttpSession.class);
        assertSame(session, p.parse(parameter("internSession"))
                .bindParameter(stub.returning("httpSession", session).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        HttpCookies cookies = proxy(HttpCookies.class);
        assertSame(cookies, p.parse(parameter("internCookies"))
                .bindParameter(stub.returning("cookies", cookies).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        HttpConnection sseSession = proxy(HttpConnection.class);
        SseResponse boundSseResponse = assertInstanceOf(SseResponse.class, p.parse(parameter("internSseResponse"))
                .bindParameter(stub.returning("getConnectSession", sseSession).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("text/event-stream", boundSseResponse.headers().get(HttpHeaderNames.CONTENT_TYPE),
                "SSE 响应由绑定器经连接会话就地构造，携事件流媒体类型");

        SseEmitter boundSseEmitter = assertInstanceOf(InternalSseEmitter.class, p.parse(parameter("internSseEmitter"))
                .bindParameter(stub.returning("getConnectSession", sseSession).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("text/event-stream", boundSseEmitter.headers().get(HttpHeaderNames.CONTENT_TYPE));
        // 消息缓存容量统一 32:未初始化时 32 条入缓存无异常,第 33 条溢出抛错
        for (int i = 0; i < 32; i++) {
            boundSseEmitter.send("m" + i);
        }
        assertThrows(SseBufferException.class, () -> boundSseEmitter.send("overflow"),
                "第 33 条缓存消息越界");

        HttpConnection connectSession = proxy(HttpConnection.class);
        assertSame(connectSession, p.parse(parameter("internConnectSession"))
                .bindParameter(stub.returning("getConnectSession", connectSession).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        // 夹具侧类型矫正（D1）：CHANNEL_BINDER 认领的声明类型是 java.nio.channels.Channel（Params.internChannel
        // 不动），但 InternalHttpConnection 存取的是 io.netty.channel.Channel（InternalHttpConnection.java:19/:30），
        // 故被注入的那个对象必须是 Netty 通道的代理；断言语义（注入的就是配置进去的那个对象）逐字保留。
        io.netty.channel.Channel channel = proxy(io.netty.channel.Channel.class);
        assertSame(channel, p.parse(parameter("internChannel"))
                .bindParameter(stub.returning("getConnectSession", new InternalHttpConnection(channel)).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void internUnsupportedTypeYieldsNoBinder() {
        assertNull(new InternTypeParameterParser().parse(parameter("internUnsupported")));
    }

    // ---------- FormParameterParser 文件形态 ----------

    @Test
    void uploadFileBindsSingleFile() throws Exception {
        HttpFormContent form = multipartContentOf(
                new String[]{"f", "a.txt", "first"}, new String[]{"other", "o.txt", "second"});
        Stub stub = new Stub().returning("content", form);
        ParameterParser.Binder b = new FormParameterParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        assertSame(form.formFiles().get("f").getFirst(),
                b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即表单字段名：单文件通道取键 \"f\" 归档的首文件，不与他字段相混");
        assertArrayEquals(new Object[0], stub.lastArgs("message"), "取数经上下文消息的内容通道");
        form.close();
    }

    @Test
    void uploadObjectTypedParameterIsNotOurs() {
        // 文件与普通字段共用 @FormParam 后，Object 声明与文本参数无法区分，不按文件认领
        assertNull(new FormParameterParser().parse(parameter("uploadObject")));
    }

    @Test
    void uploadBinderRejectsNonFormContent() {
        // 内容不是内置表单内容类时拒绑，异常文案沿用原表单取数通道口径
        Stub stub = new Stub().returning("content", new PlainContentFixture());
        ParameterParser.Binder b = new FormParameterParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        RequestParseException ex = assertThrows(RequestParseException.class,
                () -> b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data",
                ex.getMessage());
    }

    @Test
    @DisplayName("HS-16 回归：@FormParam 集合参数经内置表单内容的文件归档绑定，集合分支可达")
    void uploadCollectionTypesBindFileCollections() throws Exception {
        // 集合形参经 @FormParam 标注且元素类型为 MultipartFile 才认领，collectionType 1/2 与归档取数/HashSet 分支可达
        HttpFormContent form = multipartContentOf(
                new String[]{"f", "a.txt", "1"}, new String[]{"f", "b.txt", "2"},
                new String[]{"other", "c.txt", "3"});
        Stub stub = new Stub().returning("content", form);
        List<MultipartFile> archived = form.formFiles().get("f");
        ParameterParser.Binder listBinder = new FormParameterParser().parse(parameter("uploadList"));
        assertNotNull(listBinder, "List<MultipartFile> + @FormParam 应被认领");
        assertSame(archived, listBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即表单字段名：List 分支直传键 \"f\" 归档");
        ParameterParser.Binder setBinder = new FormParameterParser().parse(parameter("uploadSet"));
        assertNotNull(setBinder, "Set<MultipartFile> + @FormParam 应被认领");
        assertEquals(Set.copyOf(archived), setBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "Set 分支把归档结果包装成 HashSet");
        // 字段不存在:集合分支归一空集合,单文件分支归一 null
        Stub empty = new Stub().returning("content", formContentOf(""));
        assertEquals(List.of(), listBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals(Set.of(), setBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        ParameterParser.Binder singleBinder = new FormParameterParser().parse(parameter("uploadFile"));
        assertNull(singleBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "单文件通道未命中归一 null");
        // 无 @FormParam 的裸 List 不认领——不得截走其他渠道负责的参数
        assertNull(new FormParameterParser().parse(parameter("uploadListWithoutAnnotation")));
        // 声明元素类型与 MultipartFile 不符 → 不认领
        assertNull(new FormParameterParser().parse(parameter("uploadStringList")));
        // 通配符元素类型无法判定为文件 → 不认领
        assertNull(new FormParameterParser().parse(parameter("uploadWildcardList")));
        // List/Set 之外的裸集合类型 → 注册期异常，与查询侧同款
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> new FormParameterParser().parse(parameter("uploadRawCollection")));
        assertEquals("not support type of collection:java.util.Collection", ex.getMessage());
        form.close();
    }

    @Test
    @DisplayName("HS-17 回归：缺 @FormParam 的上传类型参数不认领，不再抛 NPE")
    void uploadParameterWithoutAnnotationIsNotClaimed() {
        // 注解存在性前置判定，缺 @FormParam 直接返回 null 交还解析链；
        // 判定发生在类型分流之前，MultipartFile 与 Object 两型同样不认领
        assertNull(new FormParameterParser().parse(parameter("uploadFileWithoutAnnotation")),
                "缺 @FormParam 的 MultipartFile 参数应不认领");
        assertNull(new FormParameterParser().parse(parameter("uploadObjectWithoutAnnotation")),
                "缺 @FormParam 的 Object 参数应不认领");
    }

    // ---------- BeanJsonParameterParser ----------

    @Test
    void beanJsonBindsPlainLoad() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", jsonContentOf("{\"name\":\"x\"}"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJson"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertArrayEquals(new Object[]{"{\"name\":\"x\"}", Bean.class}, serializer.lastArgs,
                "取数经内置 JSON 内容类，绑定直调序列化器 jsonToBean(体字符串,声明类型)");
        assertEquals("jsonToBean", serializer.lastMethod);
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void beanJsonValidWithoutGroupsUsesSingleArgOverload() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", jsonContentOf("{}"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJsonValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean, "校验的就是绑定出的 bean");
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void beanJsonValidWithGroupsPassesGroups() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", jsonContentOf("{}"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJsonGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void jsonBinderRejectsNonJsonContent() {
        // 内容不是内置 JSON 内容类时拒绑，异常文案沿用原 JSON 取数通道口径
        Stub stub = new Stub().returning("content", new PlainContentFixture());
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJson"));
        assertNotNull(b);
        RequestParseException ex = assertThrows(RequestParseException.class,
                () -> b.bindParameter(stub.ctx(), NO_PARAMS, new RecordingValidator(), new RecordingSerializer()));
        assertEquals("contentType is not application/json", ex.getMessage());
    }

    @Test
    void jsonWithoutAnnotationIsIgnored() {
        assertNull(new BeanJsonParameterParser().parse(parameter("beanJsonPlain")));
    }

    @Test
    void beanJsonBinderDeserializesBodyWithRealSerializer() {
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJsonBound"));
        assertNotNull(b);
        Stub stub = new Stub().returning("content",
                jsonContentOf("{\"name\":\"cindy\",\"tag\":[\"x\",\"y\"]}"));
        Bound bean = (Bound) b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, JACKSON);
        assertEquals("cindy", bean.getName());
        assertEquals(List.of("x", "y"), bean.getTag(), "体字符串经序列化器反序列化为 bean");
    }

    // ---------- BeanFormParameterParser ----------

    @Test
    void beanFormBindsPlainLoad() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", formContentOf("q=1"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanForm"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals("mapToBean", serializer.lastMethod, "表单参数折叠后经 mapToBean 通道交序列化器");
        assertEquals(Map.of("q", "1"), serializer.lastArgs[0],
                "入料是内置表单内容归档的表单参数,单值折叠为字符串,数据源断链(改传空映射)此处必红");
        assertEquals(Bean.class, serializer.lastArgs[1], "第二实参为声明类型");
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void beanFormValidWithoutGroupsUsesSingleArgOverload() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", formContentOf(""));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanFormValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean);
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void beanFormValidWithGroupsPassesGroups() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", formContentOf(""));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanFormGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void formBinderRejectsNonFormContent() {
        // 内容不是内置表单内容类时拒绑，异常文案沿用原表单取数通道口径
        Stub stub = new Stub().returning("content", new PlainContentFixture());
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanForm"));
        assertNotNull(b);
        RequestParseException ex = assertThrows(RequestParseException.class,
                () -> b.bindParameter(stub.ctx(), NO_PARAMS, new RecordingValidator(), new RecordingSerializer()));
        assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data",
                ex.getMessage());
    }

    @Test
    void formWithoutAnnotationIsIgnored() {
        assertNull(new BeanFormParameterParser().parse(parameter("beanFormPlain")));
    }

    @Test
    void beanFormBinderCollapsesSingleAndKeepsMultiValues() {
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanFormBound"));
        assertNotNull(b);
        Stub stub = new Stub().returning("content", formContentOf("name=alice&tag=a&tag=b"));
        Bound bean = (Bound) b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, JACKSON);
        assertEquals("alice", bean.getName(), "单值列表折叠为该值后绑定");
        assertEquals(List.of("a", "b"), bean.getTag(), "多值列表保留交序列化器");
    }

    // ---------- BeanQueryParameterParser ----------

    @Test
    void beanQueryBindsPlainLoad() {
        Bean sentinel = new Bean();
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQuery"));
        assertNotNull(b);
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("1")));
        assertSame(sentinel, b.bindParameter(new Stub().ctx(), params, validator, serializer));
        assertEquals("mapToBean", serializer.lastMethod, "查询参数折叠后经 mapToBean 通道交序列化器");
        assertEquals(Map.of("q", "1"), serializer.lastArgs[0],
                "入料是承载对象的查询映射,单值折叠为字符串,数据源断链(改传空映射)此处必红");
        assertEquals(Bean.class, serializer.lastArgs[1], "第二实参为声明类型");
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void beanQueryValidWithoutGroupsUsesSingleArgOverload() {
        Bean sentinel = new Bean();
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQueryValid"));
        RequestParameter params = new RequestParameter(Map.of(), Map.of("q", List.of("1")));
        assertSame(sentinel, b.bindParameter(new Stub().ctx(), params, validator, serializer));
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean);
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void beanQueryValidWithGroupsPassesGroups() {
        Bean sentinel = new Bean();
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQueryGroups"));
        assertSame(sentinel, b.bindParameter(new Stub().ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void beanQueryWithoutAnnotationIsIgnored() {
        assertNull(new BeanQueryParameterParser().parse(parameter("beanQueryPlain")));
    }

    @Test
    void beanQueryBinderCollapsesSingleAndKeepsMultiValues() {
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQueryBound"));
        assertNotNull(b);
        RequestParameter params = new RequestParameter(Map.of(),
                Map.of("name", List.of("alice"), "tag", List.of("a", "b")));
        Bound bean = (Bound) b.bindParameter(new Stub().ctx(), params, NOOP, JACKSON);
        assertEquals("alice", bean.getName(), "单值列表折叠为该值后绑定");
        assertEquals(List.of("a", "b"), bean.getTag(), "多值列表保留交序列化器");
    }

    @Test
    void beanQueryBinderPassesNullValuedFlagThrough() {
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQueryBound"));
        RequestParameter params = new RequestParameter(Map.of(),
                Map.of("name", Collections.singletonList(null)));
        Bound bean = (Bound) b.bindParameter(new Stub().ctx(), params, NOOP, JACKSON);
        assertNull(bean.getName(), "?flag 型 null 值折叠后原样传递,序列化器置属性为 null");
    }

    @Test
    void beanQueryBinderOnEmptyMapYieldsEmptyBean() {
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("beanQueryBound"));
        Bound bean = (Bound) b.bindParameter(new Stub().ctx(), NO_PARAMS, NOOP, JACKSON);
        assertNull(bean.getName());
        assertNull(bean.getTag(), "空映射绑出属性全 null 的 bean");
    }

    // ---------- PathParameterParser ----------

    @Test
    void pathParamBindsConvertedValue() {
        ParameterParser.Binder b = new PathParameterParser().parse(parameter("pathInt"));
        assertNotNull(b);
        HttpContext ctx = new Stub().ctx();
        assertEquals(42, b.bindParameter(ctx, new RequestParameter(Map.of("id", "42"), Map.of()), NOOP, NOOP_SERIALIZER), "按注解 value 从路径参数映射取键并转换");
        assertEquals(0, b.bindParameter(ctx, new RequestParameter(Map.of("nope", "42"), Map.of()), NOOP, NOOP_SERIALIZER), "映射中无同名键时取 null,int 转换器归一为 0");
    }

    @Test
    void pathWithoutAnnotationIsIgnored() {
        assertNull(new PathParameterParser().parse(parameter("pathPlain")));
    }

    @Test
    void pathUnsupportedTypeYieldsNoBinder() {
        assertNull(new PathParameterParser().parse(parameter("pathUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    // ---------- CookieParameterParser ----------

    @Test
    void cookieParamBindsValueByName() {
        ParameterParser.Binder b = new CookieParameterParser().parse(parameter("cookieString"));
        assertNotNull(b, "@CookieParam 的 String 形态应被认领");
        Stub stub = new Stub().returning("cookies", new CookiesFixture(Map.of("c", "v", "other", "x")));
        assertEquals("v", b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即 cookie 名称：经 getCookie 取值，与他名相混则取不到值必红");
        assertArrayEquals(new Object[0], stub.lastArgs("cookies"), "取数经上下文 cookies() 通道");
    }

    @Test
    void cookieMissingNormalizedByType() {
        ParameterParser.Binder stringBinder = new CookieParameterParser().parse(parameter("cookieString"));
        ParameterParser.Binder intBinder = new CookieParameterParser().parse(parameter("cookieInt"));
        Stub stub = new Stub().returning("cookies", new CookiesFixture(Map.of()));
        assertNull(stringBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "cookie 缺失 → String 转换器 null 直通");
        assertEquals(0, intBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "int 转换器对 null 归一为 0");
    }

    @Test
    void cookieUnsupportedTypeYieldsNoBinder() {
        assertNull(new CookieParameterParser().parse(parameter("cookieUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    @Test
    void cookieWithoutAnnotationIsIgnored() {
        assertNull(new CookieParameterParser().parse(parameter("cookiePlain")),
                "缺 @CookieParam 不认领");
    }

    // ---------- FormParameterParser ----------

    @Test
    void formParamBindsFirstValueOfField() {
        ParameterParser.Binder b = new FormParameterParser().parse(parameter("formString"));
        assertNotNull(b, "@FormParam 的 String 形态应被认领");
        Stub stub = new Stub().returning("content", formContentOf("f=v1&f=v2&other=x"));
        assertEquals("v1", b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即表单字段名：同名多值取首值，不与他字段相混");
        assertArrayEquals(new Object[0], stub.lastArgs("message"), "取数经上下文消息的内容通道");
    }

    @Test
    void formParamMissingNormalizedByType() {
        ParameterParser.Binder stringBinder = new FormParameterParser().parse(parameter("formString"));
        ParameterParser.Binder intBinder = new FormParameterParser().parse(parameter("formInt"));
        Stub stub = new Stub().returning("content", formContentOf(""));
        assertNull(stringBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "字段缺失 → String 转换器 null 直通");
        assertEquals(0, intBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "int 转换器对 null 归一为 0");
    }

    @Test
    void formParamBinderRejectsNonFormContent() {
        // 内容不是内置表单内容类时拒绑，异常文案沿用原表单取数通道口径
        Stub stub = new Stub().returning("content", new PlainContentFixture());
        ParameterParser.Binder b = new FormParameterParser().parse(parameter("formString"));
        assertNotNull(b);
        RequestParseException ex = assertThrows(RequestParseException.class,
                () -> b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data",
                ex.getMessage());
    }

    @Test
    void formParamUnsupportedTypeYieldsNoBinder() {
        assertNull(new FormParameterParser().parse(parameter("formUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    @Test
    void formParamWithoutAnnotationIsIgnored() {
        assertNull(new FormParameterParser().parse(parameter("formPlain")),
                "缺 @FormParam 不认领");
    }

    // ---------- HeaderParameterParser ----------

    @Test
    void headerParamBindsFirstValue() {
        ParameterParser.Binder b = new HeaderParameterParser().parse(parameter("headerString"));
        assertNotNull(b, "@HeaderParam 的 String 形态应被认领");
        Stub stub = new Stub().returning("message",
                new MessageFixture(Map.of("h", "v", "other", "x")));
        assertEquals("v", b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即头名：经请求头首值通道取值，与他名相混则取不到值必红");
        assertArrayEquals(new Object[0], stub.lastArgs("message"), "取数经上下文 message() 通道");
    }

    @Test
    void headerParamMissingNormalizedByType() {
        ParameterParser.Binder stringBinder = new HeaderParameterParser().parse(parameter("headerString"));
        ParameterParser.Binder intBinder = new HeaderParameterParser().parse(parameter("headerInt"));
        Stub stub = new Stub().returning("message", new MessageFixture(Map.of()));
        assertNull(stringBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "头缺失 → String 转换器 null 直通");
        assertEquals(0, intBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "int 转换器对 null 归一为 0");
    }

    @Test
    void headerParamUnsupportedTypeYieldsNoBinder() {
        assertNull(new HeaderParameterParser().parse(parameter("headerUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    @Test
    void headerParamWithoutAnnotationIsIgnored() {
        assertNull(new HeaderParameterParser().parse(parameter("headerPlain")),
                "缺 @HeaderParam 不认领");
    }
}
