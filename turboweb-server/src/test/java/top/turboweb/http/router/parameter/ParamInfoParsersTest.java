package top.turboweb.http.router.parameter;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.constant.ContentType;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.rest.param.*;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.serializer.JsonSerializer;
import top.turboweb.validation.BeanValidator;

import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.channels.Channel;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * autoBind 七个参数解析器各自的解析逻辑（spec §5：autoBind 七 parser 认领与绑定契约）。
 *
 * <p>分派那一侧（谁在前、谁先认领）归 Task 9；本类只钉"每个 parser 对单个
 * {@link Parameter} 怎么认领、认领后怎么绑定"。
 *
 * <p>L1 说明：`doParse` 只吃反射参数，无需上下文；`bindParameter` 需要
 * {@link HttpContext}，而它是接口 —— 用 {@link Proxy} 手写记录型替身（spec §2.2 排除 Mockito），
 * 全程只投喂内存对象，不起 socket、不碰线程、不构造 Netty 缓冲。
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
        void internConnectSession(ConnectSession v) {}
        void internChannel(Channel v) {}
        void internUnsupported(String v) {}

        // —— @UploadParam ——
        void uploadFile(@UploadParam("f") FileUpload v) {}
        void uploadObject(@UploadParam("f") Object v) {}
        void uploadList(@UploadParam("f") List<FileUpload> v) {}
        void uploadSet(@UploadParam("f") Set<FileUpload> v) {}
        void uploadListWithoutAnnotation(List<FileUpload> v) {}
        void uploadStringList(@UploadParam("f") List<String> v) {}
        void uploadRawCollection(@UploadParam("f") Collection<FileUpload> v) {}
        void uploadString(@UploadParam("f") String v) {}
        void uploadFileWithoutAnnotation(FileUpload v) {}
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

    /** 记录型 {@link HttpContext} 替身：按方法名返回预设值，并留下最后一次调用的实参。 */
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
            returns.put(method, value);
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

    /** 表单内容夹具：实现 FormContent 能力契约，向绑定器投喂指定字段与文件归档。 */
    private record FormContentFixture(
            Map<String, List<String>> params,
            Map<String, List<FileUpload>> files
    ) implements HttpContext.FormContent {

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_FORM_URLENCODED;
        }

        @Override
        public Map<String, List<String>> formParams() {
            return params;
        }

        @Override
        public Map<String, List<FileUpload>> formFiles() {
            return files;
        }
    }

    /** JSON 内容夹具：实现 JsonContent 能力契约，向绑定器投喂指定体字符串。 */
    private record JsonContentFixture(String body) implements HttpContext.JsonContent {

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_JSON;
        }

        @Override
        public String jsonString() {
            return body;
        }
    }

    /** 无通道内容夹具：只实现 Content 基础契约，供绑定器拒绑用例。 */
    private record PlainContentFixture() implements HttpContext.Content {

        @Override
        public ContentType contentType() {
            return ContentType.APPLICATION_OCTET_STREAM;
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

        SseResponse sseResponse = new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(),
                proxy(ConnectSession.class), proxy(JsonSerializer.class));
        assertSame(sseResponse, p.parse(parameter("internSseResponse"))
                .bindParameter(stub.returning("createSseResponse", sseResponse).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        SseEmitter sseEmitter = new SseEmitter(proxy(ConnectSession.class), 16) {
        };
        assertSame(sseEmitter, p.parse(parameter("internSseEmitter"))
                .bindParameter(stub.returning("createSseEmitter", sseEmitter).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        ConnectSession connectSession = proxy(ConnectSession.class);
        assertSame(connectSession, p.parse(parameter("internConnectSession"))
                .bindParameter(stub.returning("getConnectSession", connectSession).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));

        // 夹具侧类型矫正（D1）：CHANNEL_BINDER 认领的声明类型是 java.nio.channels.Channel（Params.internChannel
        // 不动），但 InternalConnectSession 存取的是 io.netty.channel.Channel（InternalConnectSession.java:27/:31），
        // 故被注入的那个对象必须是 Netty 通道的代理；断言语义（注入的就是配置进去的那个对象）逐字保留。
        io.netty.channel.Channel channel = proxy(io.netty.channel.Channel.class);
        assertSame(channel, p.parse(parameter("internChannel"))
                .bindParameter(stub.returning("getConnectSession", new InternalConnectSession(channel)).ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void internUnsupportedTypeYieldsNoBinder() {
        assertNull(new InternTypeParameterParser().parse(parameter("internUnsupported")));
    }

    // ---------- UploadParameterParser ----------

    @Test
    void uploadFileBindsSingleFile() {
        FileUpload file = proxy(FileUpload.class);
        FileUpload other = proxy(FileUpload.class);
        Stub stub = new Stub().returning("content",
                new FormContentFixture(Map.of(), Map.of("f", List.of(file), "other", List.of(other))));
        ParameterParser.Binder b = new UploadParameterParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即表单字段名：单文件通道取键 \"f\" 归档的首文件，不与他字段相混");
        assertArrayEquals(new Object[0], stub.lastArgs("content"), "取数经上下文 content() 通道");
    }

    @Test
    void uploadObjectTypedParameterBindsSingleFile() {
        // Object.isAssignableFrom(FileUpload) 为真 → 走单文件分支（collectionType 0）；
        // 归档值由夹具内容对象直供，哨兵用 FileUpload 代理，断言语义仍是"拿回的就是该字段归档的首文件"
        FileUpload file = proxy(FileUpload.class);
        Stub stub = new Stub().returning("content", new FormContentFixture(Map.of(), Map.of("f", List.of(file))));
        ParameterParser.Binder b = new UploadParameterParser().parse(parameter("uploadObject"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
    }

    @Test
    void uploadBinderRejectsNonFormContent() {
        // 内容不实现 FormContent 契约时拒绑，异常文案沿用原表单取数通道口径
        Stub stub = new Stub().returning("content", new PlainContentFixture());
        ParameterParser.Binder b = new UploadParameterParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        RequestParseException ex = assertThrows(RequestParseException.class,
                () -> b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals("contentType is not application/x-www-form-urlencoded or multipart/form-data",
                ex.getMessage());
    }

    @Test
    void uploadStringTypedParameterIsNotOurs() {
        assertNull(new UploadParameterParser().parse(parameter("uploadString")));
    }

    @Test
    @DisplayName("HS-16 回归：@UploadParam 集合参数经 FormContent 文件归档绑定，集合分支可达")
    void uploadCollectionTypesBindFileCollections() {
        // 守卫修正后：集合形参显式标注 @UploadParam 且元素类型相符才认领，collectionType 1/2 与归档取数/HashSet 分支可达
        FileUpload a = proxy(FileUpload.class);
        FileUpload b = proxy(FileUpload.class);
        Stub stub = new Stub().returning("content", new FormContentFixture(Map.of(),
                Map.of("f", List.of(a, b), "other", List.of(proxy(FileUpload.class)))));
        ParameterParser.Binder listBinder = new UploadParameterParser().parse(parameter("uploadList"));
        assertNotNull(listBinder, "List<FileUpload> + @UploadParam 应被认领");
        assertEquals(List.of(a, b), listBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER),
                "注解的 value 即表单字段名：List 分支直传键 \"f\" 归档");
        ParameterParser.Binder setBinder = new UploadParameterParser().parse(parameter("uploadSet"));
        assertNotNull(setBinder, "Set<FileUpload> + @UploadParam 应被认领");
        assertEquals(Set.of(a, b), setBinder.bindParameter(stub.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "Set 分支把归档结果包装成 HashSet");
        // 字段不存在:集合分支归一空集合,单文件分支归一 null
        Stub empty = new Stub().returning("content", new FormContentFixture(Map.of(), Map.of()));
        assertEquals(List.of(), listBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        assertEquals(Set.of(), setBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER));
        ParameterParser.Binder singleBinder = new UploadParameterParser().parse(parameter("uploadFile"));
        assertNull(singleBinder.bindParameter(empty.ctx(), NO_PARAMS, NOOP, NOOP_SERIALIZER), "单文件通道未命中归一 null");
        // 无 @UploadParam 的裸 List 不认领——不得截走其他渠道负责的参数
        assertNull(new UploadParameterParser().parse(parameter("uploadListWithoutAnnotation")));
        // 声明元素类型与 FileUpload 不符 → 不认领
        assertNull(new UploadParameterParser().parse(parameter("uploadStringList")));
        // List/Set 之外的裸集合类型 → 注册期异常，与查询侧同款
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> new UploadParameterParser().parse(parameter("uploadRawCollection")));
        assertEquals("not support type of collection:java.util.Collection", ex.getMessage());
    }

    @Test
    @DisplayName("HS-17 回归：缺 @UploadParam 的上传类型参数不认领，不再抛 NPE")
    void uploadParameterWithoutAnnotationIsNotClaimed() {
        // 修正后：注解存在性前置判定，缺 @UploadParam 直接返回 null 交还解析链，upload.value() 的 NPE 不可达；
        // 判定发生在类型分流之前，FileUpload 与 Object 两型同样不认领
        assertNull(new UploadParameterParser().parse(parameter("uploadFileWithoutAnnotation")),
                "缺 @UploadParam 的 FileUpload 参数应不认领");
        assertNull(new UploadParameterParser().parse(parameter("uploadObjectWithoutAnnotation")),
                "缺 @UploadParam 的 Object 参数应不认领");
    }

    // ---------- BeanJsonParameterParser ----------

    @Test
    void beanJsonBindsPlainLoad() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", new JsonContentFixture("{\"name\":\"x\"}"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJson"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertArrayEquals(new Object[]{"{\"name\":\"x\"}", Bean.class}, serializer.lastArgs,
                "取数经 JsonContent 契约，绑定直调序列化器 jsonToBean(体字符串,声明类型)");
        assertEquals("jsonToBean", serializer.lastMethod);
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void beanJsonValidWithoutGroupsUsesSingleArgOverload() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", new JsonContentFixture("{}"));
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
        Stub stub = new Stub().returning("content", new JsonContentFixture("{}"));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("beanJsonGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void jsonBinderRejectsNonJsonContent() {
        // 内容不实现 JsonContent 契约时拒绑，异常文案沿用原 JSON 取数通道口径
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
                new JsonContentFixture("{\"name\":\"cindy\",\"tag\":[\"x\",\"y\"]}"));
        Bound bean = (Bound) b.bindParameter(stub.ctx(), NO_PARAMS, NOOP, JACKSON);
        assertEquals("cindy", bean.getName());
        assertEquals(List.of("x", "y"), bean.getTag(), "体字符串经序列化器反序列化为 bean");
    }

    // ---------- BeanFormParameterParser ----------

    @Test
    void beanFormBindsPlainLoad() {
        Bean sentinel = new Bean();
        Map<String, List<String>> params = Map.of("q", List.of("1"));
        Stub stub = new Stub().returning("content", new FormContentFixture(params, Map.of()));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanForm"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals("mapToBean", serializer.lastMethod, "表单参数折叠后经 mapToBean 通道交序列化器");
        assertEquals(Map.of("q", "1"), serializer.lastArgs[0],
                "入料是 FormContent 归档的表单参数,单值折叠为字符串,数据源断链(改传空映射)此处必红");
        assertEquals(Bean.class, serializer.lastArgs[1], "第二实参为声明类型");
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void beanFormValidWithoutGroupsUsesSingleArgOverload() {
        Bean sentinel = new Bean();
        Stub stub = new Stub().returning("content", new FormContentFixture(Map.of(), Map.of()));
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
        Stub stub = new Stub().returning("content", new FormContentFixture(Map.of(), Map.of()));
        RecordingSerializer serializer = new RecordingSerializer().bound(sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("beanFormGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), NO_PARAMS, validator, serializer));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void formBinderRejectsNonFormContent() {
        // 内容不实现 FormContent 契约时拒绑，异常文案沿用原表单取数通道口径
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
        Stub stub = new Stub().returning("content", new FormContentFixture(
                Map.of("name", List.of("alice"), "tag", List.of("a", "b")), Map.of()));
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
}
