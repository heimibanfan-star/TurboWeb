package top.turboweb.http.middleware.router.info.autobind;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.anno.param.binder.FormModel;
import top.turboweb.anno.param.binder.JsonModel;
import top.turboweb.anno.param.binder.Param;
import top.turboweb.anno.param.binder.Query;
import top.turboweb.anno.param.binder.QueryModel;
import top.turboweb.anno.param.binder.Upload;
import top.turboweb.anno.param.parser.Parser;
import top.turboweb.commons.exception.TurboArgsValidationException;
import top.turboweb.commons.exception.TurboRouterDefinitionCreateException;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.core.connect.ConnectSession;
import top.turboweb.http.core.connect.InternalConnectSession;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.cookie.HttpCookieManager;
import top.turboweb.http.core.response.InternalCallResponse;
import top.turboweb.http.core.response.SseEmitter;
import top.turboweb.http.core.response.SseResponse;
import top.turboweb.http.core.session.HttpSession;

import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.channels.Channel;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * autoBind 七个参数解析器各自的解析逻辑（spec §5：`AnnoRouterManager` 前缀规整与 autoBind 七 parser 分派）。
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

        // —— @Query ——
        void queryString(@Query("q") String v) {}
        void queryNonNull(@Query(value = "q", nonNull = true) String v) {}
        void queryInt(@Query("n") int v) {}
        void queryList(@Query("q") List<Integer> v) {}
        void querySet(@Query("q") Set<String> v) {}
        void queryUnsupportedCollection(@Query("q") Collection<String> v) {}
        void queryUnsupportedElement(@Query("q") Map<String, String> v) {}

        // —— @Parser 分派 ——
        void parserSelf(@Parser(ProbeParser.class) String v) {}
        void parserDecides(@Parser(QueryParameterInfoParser.class) @Query("q") @Param("id") int v) {}

        // —— 内部类型（无注解，按声明类型认领） ——
        void internContext(HttpContext v) {}
        void internSession(HttpSession v) {}
        void internCookie(HttpCookieManager v) {}
        void internSseResponse(SseResponse v) {}
        void internSseEmitter(SseEmitter v) {}
        void internConnectSession(ConnectSession v) {}
        void internChannel(Channel v) {}
        void internUnsupported(String v) {}

        // —— @Upload ——
        void uploadFile(@Upload("f") FileUpload v) {}
        void uploadObject(@Upload("f") Object v) {}
        void uploadList(@Upload("f") List<FileUpload> v) {}
        void uploadSet(@Upload("f") Set<FileUpload> v) {}
        void uploadListWithoutAnnotation(List<FileUpload> v) {}
        void uploadStringList(@Upload("f") List<String> v) {}
        void uploadRawCollection(@Upload("f") Collection<FileUpload> v) {}
        void uploadString(@Upload("f") String v) {}
        void uploadFileWithoutAnnotation(FileUpload v) {}
        void uploadObjectWithoutAnnotation(Object v) {}

        // —— 模型 ——
        void jsonModel(@JsonModel Model v) {}
        void jsonValid(@JsonModel(true) Model v) {}
        void jsonGroups(@JsonModel(value = true, groups = GroupA.class) Model v) {}
        void jsonPlain(Model v) {}

        void formModel(@FormModel Model v) {}
        void formValid(@FormModel(true) Model v) {}
        void formGroups(@FormModel(value = true, groups = GroupA.class) Model v) {}
        void formPlain(Model v) {}

        void queryModel(@QueryModel Model v) {}
        void queryModelValid(@QueryModel(true) Model v) {}
        void queryModelGroups(@QueryModel(value = true, groups = GroupA.class) Model v) {}
        void queryModelPlain(Model v) {}

        // —— @Param（Rest） ——
        void restInt(@Param("id") int v) {}
        void restUnsupported(@Param("id") Map<String, String> v) {}
        void restPlain(int v) {}

        void noAnnotation(String v) {}
    }

    /** {@link AbstractParamInfoParser} 的可观测子类：记录 doParse 是否被调用。 */
    private static final class ProbeParser extends AbstractParamInfoParser {
        int doParseCalls;

        @Override
        protected ParameterBinder doParse(Parameter parameter) {
            doParseCalls++;
            return ctx -> "probe";
        }
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

    // ---------- AbstractParamInfoParser：@Parser 分派 ----------

    @Test
    void parserAnnotationRoutesToFixedDoParse() {
        ProbeParser p = new ProbeParser();
        assertNotNull(p.parse(parameter("parserSelf")), "参数指定本解析器时应放行到 doParse");
        assertEquals(1, p.doParseCalls);
    }

    @Test
    void parserAnnotationForAnotherParserYieldsNoBinder() {
        ProbeParser p = new ProbeParser();
        assertNull(p.parse(parameter("parserDecides")),
                "@Parser 指定的是 QueryParameterInfoParser，探针解析器必须短路");
        assertEquals(0, p.doParseCalls, "不匹配的 @Parser 必须在 doParse 之前 return");
    }

    @Test
    void parserAnnotationRestrictsWhichParserSeesTheParameter() {
        Parameter p = parameter("parserDecides"); // @Parser(QueryParameterInfoParser) + @Query + @Param + int
        assertNotNull(new QueryParameterInfoParser().parse(p), "被指定的解析器可以解析");
        assertNull(new RestParameterInfoParser().parse(p),
                "虽然参数带 @Param 且类型是 int，但 @Parser 把它指派给了别的解析器 → null");
    }

    // ---------- QueryParameterInfoParser ----------

    @Test
    void queryWithoutAnnotationIsIgnored() {
        assertNull(new QueryParameterInfoParser().parse(parameter("noAnnotation")));
    }

    @Test
    void queryStringBindsConvertedValue() {
        Stub stub = new Stub().returning("query", "hello");
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryString"));
        assertNotNull(b);
        assertEquals("hello", b.bindParameter(stub.ctx()));
        assertEquals("q", stub.lastArgs("query")[0], "注解的 value 即查询参数名");
    }

    @Test
    void queryUnsupportedElementTypeYieldsNoBinder() {
        assertNull(new QueryParameterInfoParser().parse(parameter("queryUnsupportedElement")),
                "Map 没有转换器 → 该参数不归本解析器");
    }

    @Test
    void queryUnsupportedCollectionTypeThrows() {
        TurboRouterDefinitionCreateException ex = assertThrows(TurboRouterDefinitionCreateException.class,
                () -> new QueryParameterInfoParser().parse(parameter("queryUnsupportedCollection")));
        assertEquals("not support type of collection:java.util.Collection", ex.getMessage());
    }

    @Test
    void queryListBindsEveryValue() {
        Stub stub = new Stub().returning("queries", List.of("1", "2"));
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryList"));
        assertNotNull(b);
        assertEquals(List.of(1, 2), b.bindParameter(stub.ctx()), "List<Integer> 逐元素转换");
        assertEquals("q", stub.lastArgs("queries")[0]);
    }

    @Test
    void queryListWithoutValuesIsEmptyList() {
        Stub stub = new Stub().returning("queries", List.of());
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryList"));
        assertEquals(List.of(), b.bindParameter(stub.ctx()));
    }

    @Test
    void querySetDeduplicates() {
        Stub stub = new Stub().returning("queries", List.of("a", "b", "a"));
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("querySet"));
        assertEquals(Set.of("a", "b"), b.bindParameter(stub.ctx()));
    }

    @Test
    void querySetWithoutValuesIsEmptySet() {
        Stub stub = new Stub().returning("queries", List.of());
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("querySet"));
        assertEquals(Set.of(), b.bindParameter(stub.ctx()));
    }

    @Test
    void queryNonNullRejectsMissingValue() {
        Stub stub = new Stub().returning("query", null);
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryNonNull"));
        TurboArgsValidationException ex = assertThrows(TurboArgsValidationException.class,
                () -> b.bindParameter(stub.ctx()));
        assertEquals(List.of("q不能为null"), ex.getErrorMsg());
    }

    @Test
    void queryNonNullPassesWhenValuePresent() {
        Stub stub = new Stub().returning("query", "v");
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryNonNull"));
        assertEquals("v", b.bindParameter(stub.ctx()));
    }

    @Test
    void queryPrimitiveIntDefaultsToZeroWhenMissing() {
        Stub stub = new Stub().returning("query", null);
        ParameterBinder b = new QueryParameterInfoParser().parse(parameter("queryInt"));
        assertEquals(0, b.bindParameter(stub.ctx()), "int 转换器对 null 返回 0（与 Integer 的 null 区分）");
    }

    // ---------- InternTypeParamInfoParser ----------

    @Test
    void internBindsEachSupportedInternalType() {
        Stub stub = new Stub();
        HttpContext ctx = stub.ctx();
        InternTypeParamInfoParser p = new InternTypeParamInfoParser();

        assertSame(ctx, p.parse(parameter("internContext")).bindParameter(ctx),
                "HttpContext 参数注入的就是当前上下文");

        HttpSession session = proxy(HttpSession.class);
        assertSame(session, p.parse(parameter("internSession"))
                .bindParameter(stub.returning("httpSession", session).ctx()));

        HttpCookieManager cookie = proxy(HttpCookieManager.class);
        assertSame(cookie, p.parse(parameter("internCookie"))
                .bindParameter(stub.returning("cookie", cookie).ctx()));

        SseResponse sseResponse = new SseResponse(HttpResponseStatus.OK, new DefaultHttpHeaders(),
                proxy(ConnectSession.class), proxy(JsonSerializer.class));
        assertSame(sseResponse, p.parse(parameter("internSseResponse"))
                .bindParameter(stub.returning("createSseResponse", sseResponse).ctx()));

        SseEmitter sseEmitter = new SseEmitter(proxy(ConnectSession.class), 16) {
            @Override
            public InternalCallResponse.InternalCallType getType() {
                return InternalCallResponse.InternalCallType.SSE;
            }
        };
        assertSame(sseEmitter, p.parse(parameter("internSseEmitter"))
                .bindParameter(stub.returning("createSseEmitter", sseEmitter).ctx()));

        ConnectSession connectSession = proxy(ConnectSession.class);
        assertSame(connectSession, p.parse(parameter("internConnectSession"))
                .bindParameter(stub.returning("getConnectSession", connectSession).ctx()));

        // 夹具侧类型矫正（D1）：CHANNEL_BINDER 认领的声明类型是 java.nio.channels.Channel（Params.internChannel
        // 不动），但 InternalConnectSession 存取的是 io.netty.channel.Channel（InternalConnectSession.java:27/:31），
        // 故被注入的那个对象必须是 Netty 通道的代理；断言语义（注入的就是配置进去的那个对象）逐字保留。
        io.netty.channel.Channel channel = proxy(io.netty.channel.Channel.class);
        assertSame(channel, p.parse(parameter("internChannel"))
                .bindParameter(stub.returning("getConnectSession", new InternalConnectSession(channel)).ctx()));
    }

    @Test
    void internUnsupportedTypeYieldsNoBinder() {
        assertNull(new InternTypeParamInfoParser().parse(parameter("internUnsupported")));
    }

    // ---------- UploadParameterInfoParser ----------

    @Test
    void uploadFileBindsSingleFile() {
        FileUpload file = proxy(FileUpload.class);
        Stub stub = new Stub().returning("loadFile", file);
        ParameterBinder b = new UploadParameterInfoParser().parse(parameter("uploadFile"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx()));
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
        ParameterBinder b = new UploadParameterInfoParser().parse(parameter("uploadObject"));
        assertNotNull(b);
        assertSame(file, b.bindParameter(stub.ctx()));
    }

    @Test
    void uploadStringTypedParameterIsNotOurs() {
        assertNull(new UploadParameterInfoParser().parse(parameter("uploadString")));
    }

    @Test
    @DisplayName("HS-16 回归：@Upload 集合参数走 loadFiles 绑定，集合分支可达")
    void uploadCollectionTypesBindFileCollections() {
        // 守卫修正后：集合形参显式标注 @Upload 且元素类型相符才认领，collectionType 1/2 与 loadFiles/HashSet 分支可达
        FileUpload a = proxy(FileUpload.class);
        FileUpload b = proxy(FileUpload.class);
        Stub stub = new Stub().returning("loadFiles", List.of(a, b));
        ParameterBinder listBinder = new UploadParameterInfoParser().parse(parameter("uploadList"));
        assertNotNull(listBinder, "List<FileUpload> + @Upload 应被认领");
        assertEquals(List.of(a, b), listBinder.bindParameter(stub.ctx()));
        assertEquals("f", stub.lastArgs("loadFiles")[0], "注解的 value 即表单字段名");
        ParameterBinder setBinder = new UploadParameterInfoParser().parse(parameter("uploadSet"));
        assertNotNull(setBinder, "Set<FileUpload> + @Upload 应被认领");
        assertEquals(Set.of(a, b), setBinder.bindParameter(stub.ctx()), "Set 分支把 loadFiles 结果包装成 HashSet");
        // 字段缺失（loadFiles 返回 null）归一为空集合
        Stub empty = new Stub().returning("loadFiles", null);
        assertEquals(List.of(), listBinder.bindParameter(empty.ctx()));
        assertEquals(Set.of(), setBinder.bindParameter(empty.ctx()));
        // 无 @Upload 的裸 List 不认领——不得截走其他渠道负责的参数
        assertNull(new UploadParameterInfoParser().parse(parameter("uploadListWithoutAnnotation")));
        // 声明元素类型与 FileUpload 不符 → 不认领
        assertNull(new UploadParameterInfoParser().parse(parameter("uploadStringList")));
        // List/Set 之外的裸集合类型 → 注册期异常，与查询侧同款
        TurboRouterDefinitionCreateException ex = assertThrows(TurboRouterDefinitionCreateException.class,
                () -> new UploadParameterInfoParser().parse(parameter("uploadRawCollection")));
        assertEquals("not support type of collection:java.util.Collection", ex.getMessage());
    }

    @Test
    @DisplayName("HS-17 回归：缺 @Upload 的上传类型参数不认领，不再抛 NPE")
    void uploadParameterWithoutAnnotationIsNotClaimed() {
        // 修正后：注解存在性前置判定，缺 @Upload 直接返回 null 交还解析链，upload.value() 的 NPE 不可达；
        // 判定发生在类型分流之前，FileUpload 与 Object 两型同样不认领
        assertNull(new UploadParameterInfoParser().parse(parameter("uploadFileWithoutAnnotation")),
                "缺 @Upload 的 FileUpload 参数应不认领");
        assertNull(new UploadParameterInfoParser().parse(parameter("uploadObjectWithoutAnnotation")),
                "缺 @Upload 的 Object 参数应不认领");
    }

    // ---------- JsonModelParameterInfoParser ----------

    @Test
    void jsonModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadJson", sentinel);
        ParameterBinder b = new JsonModelParameterInfoParser().parse(parameter("jsonModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadJson"), "loadJson(参数声明类型)");
    }

    @Test
    void jsonValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidJson", sentinel);
        ParameterBinder b = new JsonModelParameterInfoParser().parse(parameter("jsonValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertEquals(1, stub.lastArgs("loadValidJson").length, "无分组时调 loadValidJson(Class)");
    }

    @Test
    void jsonValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidJson", sentinel);
        ParameterBinder b = new JsonModelParameterInfoParser().parse(parameter("jsonGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        Object[] args = stub.lastArgs("loadValidJson");
        assertEquals(2, args.length, "有分组时调 loadValidJson(Class, Class...)");
        assertArrayEquals(new Class<?>[]{GroupA.class}, (Class<?>[]) args[1]);
    }

    @Test
    void jsonWithoutAnnotationIsIgnored() {
        assertNull(new JsonModelParameterInfoParser().parse(parameter("jsonPlain")));
    }

    // ---------- FormModelParameterInfoParser ----------

    @Test
    void formModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadForm", sentinel);
        ParameterBinder b = new FormModelParameterInfoParser().parse(parameter("formModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadForm"));
    }

    @Test
    void formValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidForm", sentinel);
        ParameterBinder b = new FormModelParameterInfoParser().parse(parameter("formValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertEquals(1, stub.lastArgs("loadValidForm").length);
    }

    @Test
    void formValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidForm", sentinel);
        ParameterBinder b = new FormModelParameterInfoParser().parse(parameter("formGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        Object[] args = stub.lastArgs("loadValidForm");
        assertEquals(2, args.length);
        assertArrayEquals(new Class<?>[]{GroupA.class}, (Class<?>[]) args[1]);
    }

    @Test
    void formWithoutAnnotationIsIgnored() {
        assertNull(new FormModelParameterInfoParser().parse(parameter("formPlain")));
    }

    // ---------- QueryModelParameterInfoParser ----------

    @Test
    void queryModelBindsPlainLoad() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadQuery", sentinel);
        ParameterBinder b = new QueryModelParameterInfoParser().parse(parameter("queryModel"));
        assertNotNull(b);
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertArrayEquals(new Object[]{Model.class}, stub.lastArgs("loadQuery"));
    }

    @Test
    void queryModelValidWithoutGroupsUsesSingleArgOverload() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidQuery", sentinel);
        ParameterBinder b = new QueryModelParameterInfoParser().parse(parameter("queryModelValid"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        assertEquals(1, stub.lastArgs("loadValidQuery").length);
    }

    @Test
    void queryModelValidWithGroupsPassesGroups() {
        Model sentinel = new Model();
        Stub stub = new Stub().returning("loadValidQuery", sentinel);
        ParameterBinder b = new QueryModelParameterInfoParser().parse(parameter("queryModelGroups"));
        assertSame(sentinel, b.bindParameter(stub.ctx()));
        Object[] args = stub.lastArgs("loadValidQuery");
        assertEquals(2, args.length);
        assertArrayEquals(new Class<?>[]{GroupA.class}, (Class<?>[]) args[1]);
    }

    @Test
    void queryModelWithoutAnnotationIsIgnored() {
        assertNull(new QueryModelParameterInfoParser().parse(parameter("queryModelPlain")));
    }

    // ---------- RestParameterInfoParser ----------

    @Test
    void restParamBindsConvertedValue() {
        Stub stub = new Stub().returning("param", "42");
        ParameterBinder b = new RestParameterInfoParser().parse(parameter("restInt"));
        assertNotNull(b);
        assertEquals(42, b.bindParameter(stub.ctx()));
        assertEquals("id", stub.lastArgs("param")[0], "注解的 value 即路径参数名");
    }

    @Test
    void restWithoutAnnotationIsIgnored() {
        assertNull(new RestParameterInfoParser().parse(parameter("restPlain")));
    }

    @Test
    void restUnsupportedTypeYieldsNoBinder() {
        assertNull(new RestParameterInfoParser().parse(parameter("restUnsupported")),
                "Map 没有转换器 → 该参数不归本解析器");
    }
}
