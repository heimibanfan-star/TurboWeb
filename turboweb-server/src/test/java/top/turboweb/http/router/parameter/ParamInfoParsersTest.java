package top.turboweb.http.router.parameter;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.core.cookie.HttpCookies;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;
import top.turboweb.http.exception.RouteHandlerException;
import top.turboweb.http.exception.RouteHandlerException.ErrorCode;
import top.turboweb.rest.param.*;
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

        // —— 模型 ——
        void jsonModel(@BeanJson Model v) {}
        void jsonValid(@BeanJson(true) Model v) {}
        void jsonGroups(@BeanJson(value = true, groups = GroupA.class) Model v) {}
        void jsonPlain(Model v) {}

        void formModel(@BeanForm Model v) {}
        void formValid(@BeanForm(true) Model v) {}
        void formGroups(@BeanForm(value = true, groups = GroupA.class) Model v) {}
        void formPlain(Model v) {}

        void queryModel(@BeanQuery Model v) {}
        void queryModelValid(@BeanQuery(true) Model v) {}
        void queryModelGroups(@BeanQuery(value = true, groups = GroupA.class) Model v) {}
        void queryModelPlain(Model v) {}

        // —— @PathParam（Rest） ——
        void restInt(@PathParam("id") int v) {}
        void restUnsupported(@PathParam("id") Map<String, String> v) {}
        void restPlain(int v) {}

        void noAnnotation(String v) {}
    }

    private static final class Model {}

    private static final class GroupA {}

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
        Stub stub = new Stub().returning("query", "hello");
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryString"));
        assertNotNull(b);
        assertEquals("hello", b.bindParameter(stub.ctx(), Map.of(), NOOP));
        assertEquals("q", stub.lastArgs("query")[0], "注解的 value 即查询参数名");
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
        Stub stub = new Stub().returning("queries", List.of("1", "2"));
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryList"));
        assertNotNull(b);
        assertEquals(List.of(1, 2), b.bindParameter(stub.ctx(), Map.of(), NOOP), "List<Integer> 逐元素转换");
        assertEquals("q", stub.lastArgs("queries")[0]);
    }

    @Test
    void queryListWithoutValuesIsEmptyList() {
        Stub stub = new Stub().returning("queries", List.of());
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryList"));
        assertEquals(List.of(), b.bindParameter(stub.ctx(), Map.of(), NOOP));
    }

    @Test
    void querySetDeduplicates() {
        Stub stub = new Stub().returning("queries", List.of("a", "b", "a"));
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("querySet"));
        assertEquals(Set.of("a", "b"), b.bindParameter(stub.ctx(), Map.of(), NOOP));
    }

    @Test
    void querySetWithoutValuesIsEmptySet() {
        Stub stub = new Stub().returning("queries", List.of());
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("querySet"));
        assertEquals(Set.of(), b.bindParameter(stub.ctx(), Map.of(), NOOP));
    }

    @Test
    void queryNonNullRejectsMissingValue() {
        Stub stub = new Stub().returning("query", null);
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryNonNull"));
        RouteHandlerException ex = assertThrows(RouteHandlerException.class,
                () -> b.bindParameter(stub.ctx(), Map.of(), NOOP));
        assertEquals("校验失败:[q must not be null]", ex.getMessage());
        assertEquals(ErrorCode.MissingParamError, ex.getCode());
        assertEquals(10005, ex.getCode().getValue(), "数值错误码钉住");
    }

    @Test
    void queryNonNullPassesWhenValuePresent() {
        Stub stub = new Stub().returning("query", "v");
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryNonNull"));
        assertEquals("v", b.bindParameter(stub.ctx(), Map.of(), NOOP));
    }

    @Test
    void queryPrimitiveIntDefaultsToZeroWhenMissing() {
        Stub stub = new Stub().returning("query", null);
        ParameterParser.Binder b = new QueryParameterParser().parse(parameter("queryInt"));
        assertEquals(0, b.bindParameter(stub.ctx(), Map.of(), NOOP), "int 转换器对 null 返回 0（与 Integer 的 null 区分）");
    }

    // ---------- InternTypeParameterParser ----------

    @Test
    void internBindsEachSupportedInternalType() {
        Stub stub = new Stub();
        HttpContext ctx = stub.ctx();
        InternTypeParameterParser p = new InternTypeParameterParser();

        assertSame(ctx, p.parse(parameter("internContext")).bindParameter(ctx, Map.of(), NOOP),
                "HttpContext 参数注入的就是当前上下文");

        HttpSession session = proxy(HttpSession.class);
        assertSame(session, p.parse(parameter("internSession"))
                .bindParameter(stub.returning("httpSession", session).ctx(), Map.of(), NOOP));

        HttpCookies cookies = proxy(HttpCookies.class);
        assertSame(cookies, p.parse(parameter("internCookies"))
                .bindParameter(stub.returning("cookies", cookies).ctx(), Map.of(), NOOP));

        SseResponse sseResponse = new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(),
                proxy(ConnectSession.class), proxy(JsonSerializer.class));
        assertSame(sseResponse, p.parse(parameter("internSseResponse"))
                .bindParameter(stub.returning("createSseResponse", sseResponse).ctx(), Map.of(), NOOP));

        SseEmitter sseEmitter = new SseEmitter(proxy(ConnectSession.class), 16) {
        };
        assertSame(sseEmitter, p.parse(parameter("internSseEmitter"))
                .bindParameter(stub.returning("createSseEmitter", sseEmitter).ctx(), Map.of(), NOOP));

        ConnectSession connectSession = proxy(ConnectSession.class);
        assertSame(connectSession, p.parse(parameter("internConnectSession"))
                .bindParameter(stub.returning("getConnectSession", connectSession).ctx(), Map.of(), NOOP));

        // 夹具侧类型矫正（D1）：CHANNEL_BINDER 认领的声明类型是 java.nio.channels.Channel（Params.internChannel
        // 不动），但 InternalConnectSession 存取的是 io.netty.channel.Channel（InternalConnectSession.java:27/:31），
        // 故被注入的那个对象必须是 Netty 通道的代理；断言语义（注入的就是配置进去的那个对象）逐字保留。
        io.netty.channel.Channel channel = proxy(io.netty.channel.Channel.class);
        assertSame(channel, p.parse(parameter("internChannel"))
                .bindParameter(stub.returning("getConnectSession", new InternalConnectSession(channel)).ctx(), Map.of(), NOOP));
    }

    @Test
    void internUnsupportedTypeYieldsNoBinder() {
        assertNull(new InternTypeParameterParser().parse(parameter("internUnsupported")));
    }

    // ---------- UploadParameterParser ----------

    @Test
    void uploadFileBindsSingleFile() {
        FileUpload file = proxy(FileUpload.class);
        Stub stub = new Stub().returning("loadFile", file);
        ParameterParser.Binder b = new UploadParameterParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx(), Map.of(), NOOP));
        assertEquals("f", stub.lastArgs("loadFile")[0]);
    }

    @Test
    void uploadObjectTypedParameterBindsSingleFile() {
        // Object.isAssignableFrom(FileUpload) 为真 → 走单文件分支（collectionType 0）
        // 夹具侧类型矫正（D2）：loadFile 的声明返回类型是 FileUpload（ParamBinder.java:203），
        // JDK 动态代理在把返回值交给调用方之前先按该类型强转，故哨兵不可能是 brief 夹具里的 "single"
        // （String 会抛 ClassCastException）；换成 FileUpload 哨兵代理，断言语义仍是"拿回的就是 loadFile 的那个对象"。
        FileUpload file = proxy(FileUpload.class);
        Stub stub = new Stub().returning("loadFile", file);
        ParameterParser.Binder b = new UploadParameterParser().parse(parameter("uploadObject"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx(), Map.of(), NOOP));
    }

    @Test
    void uploadStringTypedParameterIsNotOurs() {
        assertNull(new UploadParameterParser().parse(parameter("uploadString")));
    }

    @Test
    @DisplayName("HS-16 回归：@UploadParam 集合参数走 loadFiles 绑定，集合分支可达")
    void uploadCollectionTypesBindFileCollections() {
        // 守卫修正后：集合形参显式标注 @UploadParam 且元素类型相符才认领，collectionType 1/2 与 loadFiles/HashSet 分支可达
        FileUpload a = proxy(FileUpload.class);
        FileUpload b = proxy(FileUpload.class);
        Stub stub = new Stub().returning("loadFiles", List.of(a, b));
        ParameterParser.Binder listBinder = new UploadParameterParser().parse(parameter("uploadList"));
        assertNotNull(listBinder, "List<FileUpload> + @UploadParam 应被认领");
        assertEquals(List.of(a, b), listBinder.bindParameter(stub.ctx(), Map.of(), NOOP));
        assertEquals("f", stub.lastArgs("loadFiles")[0], "注解的 value 即表单字段名");
        ParameterParser.Binder setBinder = new UploadParameterParser().parse(parameter("uploadSet"));
        assertNotNull(setBinder, "Set<FileUpload> + @UploadParam 应被认领");
        assertEquals(Set.of(a, b), setBinder.bindParameter(stub.ctx(), Map.of(), NOOP), "Set 分支把 loadFiles 结果包装成 HashSet");
        // 字段缺失（loadFiles 返回 null）归一为空集合
        Stub empty = new Stub().returning("loadFiles", null);
        assertEquals(List.of(), listBinder.bindParameter(empty.ctx(), Map.of(), NOOP));
        assertEquals(Set.of(), setBinder.bindParameter(empty.ctx(), Map.of(), NOOP));
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
    void jsonModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadJson", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("jsonModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadJson"), "loadJson(参数声明类型)");
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void jsonValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadJson", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("jsonValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadJson"), "取数走无校验的 loadJson");
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean, "校验的就是绑定出的 bean");
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void jsonValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadJson", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanJsonParameterParser().parse(parameter("jsonGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void jsonWithoutAnnotationIsIgnored() {
        assertNull(new BeanJsonParameterParser().parse(parameter("jsonPlain")));
    }

    // ---------- BeanFormParameterParser ----------

    @Test
    void formModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadForm", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("formModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadForm"));
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void formValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadForm", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("formValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadForm"), "取数走无校验的 loadForm");
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean);
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void formValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadForm", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanFormParameterParser().parse(parameter("formGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void formWithoutAnnotationIsIgnored() {
        assertNull(new BeanFormParameterParser().parse(parameter("formPlain")));
    }

    // ---------- BeanQueryParameterParser ----------

    @Test
    void queryModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadQuery", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("queryModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadQuery"));
        assertEquals(0, validator.count, "validate=false 不触碰校验器");
    }

    @Test
    void queryModelValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadQuery", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("queryModelValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadQuery"), "取数走无校验的 loadQuery");
        assertEquals(1, validator.count);
        assertSame(sentinel, validator.bean);
        assertNull(validator.groups, "无分组时经校验器单参重载 validate(bean)");
    }

    @Test
    void queryModelValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadQuery", sentinel);
        RecordingValidator validator = new RecordingValidator();
        ParameterParser.Binder b = new BeanQueryParameterParser().parse(parameter("queryModelGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx(), Map.of(), validator));
        assertEquals(1, validator.count);
        assertArrayEquals(new Class<?>[]{GroupA.class}, validator.groups, "注解分组透传给校验器");
    }

    @Test
    void queryModelWithoutAnnotationIsIgnored() {
        assertNull(new BeanQueryParameterParser().parse(parameter("queryModelPlain")));
    }

    // ---------- PathParameterParser ----------

    @Test
    void restParamBindsConvertedValue() {
        ParameterParser.Binder b = new PathParameterParser().parse(parameter("restInt"));
        assertNotNull(b);
        HttpContext ctx = new Stub().ctx();
        assertEquals(42, b.bindParameter(ctx, Map.of("id", "42"), NOOP), "按注解 value 从路径参数映射取键并转换");
        assertEquals(0, b.bindParameter(ctx, Map.of("nope", "42"), NOOP), "映射中无同名键时取 null,int 转换器归一为 0");
    }

    @Test
    void restWithoutAnnotationIsIgnored() {
        assertNull(new PathParameterParser().parse(parameter("restPlain")));
    }

    @Test
    void restUnsupportedTypeYieldsNoBinder() {
        assertNull(new PathParameterParser().parse(parameter("restUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }
}
