package top.turboweb.server.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ResourceManager} 及其建造者的行为用例：登记约束、默认命名、按名与按类型查找、统一释放。
 */
class ResourceManagerTest {

    /** 关闭时向共享轨迹追加自身名字的资源，可指定关闭时抛出的异常 */
    private static class TraceResource implements AutoCloseable {
        private final String name;
        private final List<String> trace;
        private final RuntimeException failure;

        TraceResource(String name, List<String> trace) {
            this(name, trace, null);
        }

        TraceResource(String name, List<String> trace, RuntimeException failure) {
            this.name = name;
            this.trace = trace;
            this.failure = failure;
        }

        @Override
        public void close() {
            trace.add(name);
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** 子类型资源：按父类型查找应命中 */
    private static class SubResource extends TraceResource {
        SubResource(String name, List<String> trace) {
            super(name, trace);
        }
    }

    /** 双大写字母开头的类名：默认名应保持原样不转小写 */
    private static class URLResource implements AutoCloseable {
        @Override
        public void close() {
        }
    }

    /** 单字符类名：默认名走首字母小写分支 */
    private static class X implements AutoCloseable {
        @Override
        public void close() {
        }
    }

    /** 值相等但实例不同的资源：判重按对象同一性，两者都应允许登记 */
    private static class EqualableResource implements AutoCloseable {
        private final int id;

        EqualableResource(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualableResource other && other.id == id;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }

        @Override
        public void close() {
        }
    }

    // ---------- 建造者：登记约束 ----------

    @Test
    void builderRejectsBlankName() {
        ResourceManager.Builder builder = new ResourceManager.Builder();
        for (String blank : Arrays.asList(null, "", " ", "\t")) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> builder.addResource(blank, new TraceResource("r", new ArrayList<>())));
            assertEquals("resourceName can not be blank", ex.getMessage());
        }
    }

    @Test
    void builderRejectsNullResource() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResourceManager.Builder().addResource("r", null));
        assertEquals("resource must not be null", ex.getMessage());
    }

    @Test
    void builderRejectsNullResourceOnUnnamedAdd() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResourceManager.Builder().addResource(null));
        assertEquals("resource must not be null", ex.getMessage());
    }

    @Test
    void builderRejectsDuplicateName() {
        List<String> trace = new ArrayList<>();
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addResource("db", new TraceResource("first", trace));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.addResource("db", new TraceResource("second", trace)));
        assertEquals("Duplicate resource name: db", ex.getMessage());
    }

    @Test
    void builderRejectsDuplicateInstanceOnBothAddForms() {
        List<String> trace = new ArrayList<>();
        TraceResource resource = new TraceResource("first", trace);
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addResource("first", resource);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.addResource("second", resource));
        assertEquals("Duplicate resource: " + resource.getClass(), ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> builder.addResource(resource));
    }

    @Test
    void equalButDistinctInstancesAreBothAccepted() {
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new EqualableResource(1))
                .addResource("b", new EqualableResource(1))
                .build();
        assertNotNull(manager.getResource("a"));
        assertNotNull(manager.getResource("b"));
    }

    @Test
    void addResourceReturnsBuilderForChaining() {
        ResourceManager.Builder builder = new ResourceManager.Builder();
        assertSame(builder, builder.addResource("r", new TraceResource("a", new ArrayList<>())));
    }

    // ---------- 建造者：默认命名 ----------

    @Test
    void unnamedResourceDefaultsToLowerCamelCaseName() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().addResource(resource).build();
        assertSame(resource, manager.getResource("traceResource"));
    }

    @Test
    void defaultNameKeepsTwoLeadingUppercaseLetters() {
        URLResource resource = new URLResource();
        ResourceManager manager = new ResourceManager.Builder().addResource(resource).build();
        assertSame(resource, manager.getResource("URLResource"));
    }

    @Test
    void singleCharClassNameLowercasesFirstLetter() {
        X resource = new X();
        ResourceManager manager = new ResourceManager.Builder().addResource(resource).build();
        assertSame(resource, manager.getResource("x"));
    }

    @Test
    void anonymousClassRequiresExplicitName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ResourceManager.Builder().addResource(new AutoCloseable() {
                    @Override
                    public void close() {
                    }
                }));
        assertEquals("Cannot derive a default resource name for an anonymous class, please specify the name explicitly", ex.getMessage());
    }

    // ---------- 按名字查找 ----------

    @Test
    void getResourceByNameReturnsRegisteredInstance() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().addResource("a", resource).build();
        assertSame(resource, manager.getResource("a"));
    }

    @Test
    void getResourceByNameThrowsWhenAbsent() {
        ResourceManager manager = new ResourceManager.Builder().build();
        ResourceException ex = assertThrows(ResourceException.class, () -> manager.getResource("missing"));
        assertEquals("Resource not found: missing", ex.getMessage());
    }

    @Test
    void getResourceByNameAndTypeReturnsTypedInstance() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().addResource("a", resource).build();
        assertSame(resource, manager.getResource("a", TraceResource.class));
    }

    @Test
    void getResourceByNameAndTypeThrowsWhenAbsent() {
        ResourceManager manager = new ResourceManager.Builder().build();
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getResource("missing", TraceResource.class));
        assertEquals("Resource not found: missing", ex.getMessage());
    }

    @Test
    void getResourceByNameAndTypeThrowsOnTypeMismatch() {
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new TraceResource("a", new ArrayList<>()))
                .build();
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getResource("a", URLResource.class));
        assertTrue(ex.getMessage().startsWith("The type of resource a does not match the expected type: "));
    }

    @Test
    void getResourceByNameAndTypeRejectsNullType() {
        ResourceManager manager = new ResourceManager.Builder().build();
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> manager.getResource("a", null));
        assertEquals("type must not be null", ex.getMessage());
    }

    // ---------- 按类型查找 ----------

    @Test
    void getResourceByTypeReturnsSingleMatch() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", resource)
                .addResource("u", new URLResource())
                .build();
        assertSame(resource, manager.getResource(TraceResource.class));
    }

    @Test
    void getResourceByTypeMatchesSubtypes() {
        SubResource sub = new SubResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().addResource("a", sub).build();
        assertSame(sub, manager.getResource(TraceResource.class));
    }

    @Test
    void getResourceByTypeThrowsWhenNoneMatches() {
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new TraceResource("a", new ArrayList<>()))
                .build();
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getResource(URLResource.class));
        assertEquals("No resource found for type " + URLResource.class.getName(), ex.getMessage());
    }

    @Test
    void getResourceByTypeThrowsWhenMultipleMatch() {
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new TraceResource("a", new ArrayList<>()))
                .addResource("b", new TraceResource("b", new ArrayList<>()))
                .build();
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getResource(TraceResource.class));
        assertTrue(ex.getMessage().startsWith("Multiple resources found for type " + TraceResource.class.getName() + ": "));
    }

    @Test
    void getResourceByTypeRejectsNullType() {
        ResourceManager manager = new ResourceManager.Builder().build();
        Class<? extends AutoCloseable> type = null;
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> manager.getResource(type));
        assertEquals("type must not be null", ex.getMessage());
    }

    // ---------- 统一释放 ----------

    @Test
    void freeClosesAllResourcesInRegistrationOrderAndIsIdempotent() {
        List<String> trace = new ArrayList<>();
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new TraceResource("a", trace))
                .addResource("b", new TraceResource("b", trace))
                .build();
        manager.free();
        assertEquals(List.of("a", "b"), trace);
        manager.free();
        assertEquals(List.of("a", "b"), trace);
    }

    @Test
    void freeClosesRemainingResourcesWhenOneFails() {
        List<String> trace = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("boom");
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", new TraceResource("a", trace, failure))
                .addResource("b", new TraceResource("b", trace))
                .build();
        manager.free();
        assertEquals(List.of("a", "b"), trace);
    }

    @Test
    void builderAdditionsAfterBuildDoNotAffectBuiltManager() {
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addResource("a", new TraceResource("a", new ArrayList<>()));
        ResourceManager manager = builder.build();
        builder.addResource("b", new TraceResource("b", new ArrayList<>()));
        assertThrows(ResourceException.class, () -> manager.getResource("b"));
    }

    // ---------- 内置空间：build 一次性传入 ----------

    @Test
    void builtinResourcesPassedAtBuildAreQueryableByName() {
        TraceResource resource = new TraceResource("b", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().build(Map.of("bi", resource));
        assertSame(resource, manager.getBuiltinResource("bi"));
    }

    @Test
    void getBuiltinResourceByNameAndTypeReturnsTypedInstance() {
        TraceResource resource = new TraceResource("b", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().build(Map.of("bi", resource));
        assertSame(resource, manager.getBuiltinResource("bi", TraceResource.class));
    }

    @Test
    void getBuiltinResourceByNameAndTypeThrowsOnTypeMismatch() {
        ResourceManager manager = new ResourceManager.Builder()
                .build(Map.of("bi", new TraceResource("b", new ArrayList<>())));
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getBuiltinResource("bi", URLResource.class));
        assertTrue(ex.getMessage().startsWith("The type of resource bi does not match the expected type: "));
    }

    @Test
    void getBuiltinResourceByNameAndTypeRejectsNullType() {
        ResourceManager manager = new ResourceManager.Builder().build();
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> manager.getBuiltinResource("bi", null));
        assertEquals("type must not be null", ex.getMessage());
    }

    @Test
    void getBuiltinResourceThrowsWhenAbsent() {
        ResourceManager manager = new ResourceManager.Builder().build();
        ResourceException ex = assertThrows(ResourceException.class,
                () -> manager.getBuiltinResource("missing"));
        assertEquals("Resource not found: missing", ex.getMessage());
    }

    @Test
    void builtinResourcesAreNotVisibleToCustomQueries() {
        TraceResource resource = new TraceResource("b", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder().build(Map.of("bi", resource));
        assertThrows(ResourceException.class, () -> manager.getResource("bi"));
        assertThrows(ResourceException.class, () -> manager.getResource("bi", TraceResource.class));
        assertThrows(ResourceException.class, () -> manager.getResource(TraceResource.class));
    }

    @Test
    void customResourcesAreNotVisibleToBuiltinQueries() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("a", resource)
                .build();
        assertThrows(ResourceException.class, () -> manager.getBuiltinResource("a"));
    }

    @Test
    void sameNameAllowedAcrossBuiltinAndCustomSpaces() {
        TraceResource builtin = new TraceResource("b", new ArrayList<>());
        TraceResource custom = new TraceResource("c", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder()
                .addResource("same", custom)
                .build(Map.of("same", builtin));
        assertSame(builtin, manager.getBuiltinResource("same"));
        assertSame(custom, manager.getResource("same"));
    }

    @Test
    void buildRejectsNullBuiltinMap() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResourceManager.Builder().build(null));
        assertEquals("builtinResources must not be null", ex.getMessage());
    }

    @Test
    void buildRejectsBlankBuiltinName() {
        for (String blank : Arrays.asList(null, "", " ")) {
            Map<String, AutoCloseable> builtins = new HashMap<>();
            builtins.put(blank, new TraceResource("r", new ArrayList<>()));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new ResourceManager.Builder().build(builtins));
            assertEquals("resourceName can not be blank", ex.getMessage());
        }
    }

    @Test
    void buildRejectsNullBuiltinResource() {
        Map<String, AutoCloseable> builtins = new HashMap<>();
        builtins.put("bi", null);
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResourceManager.Builder().build(builtins));
        assertEquals("resource must not be null", ex.getMessage());
    }

    @Test
    void buildRejectsBuiltinInstanceAlreadyRegisteredInChain() {
        TraceResource resource = new TraceResource("a", new ArrayList<>());
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addResource("a", resource);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build(Map.of("bi", resource)));
        assertEquals("Duplicate resource: " + resource.getClass(), ex.getMessage());
    }

    @Test
    void buildRejectsSameInstanceTwiceInBuiltinMap() {
        TraceResource resource = new TraceResource("b", new ArrayList<>());
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put("bi1", resource);
        builtins.put("bi2", resource);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new ResourceManager.Builder().build(builtins));
        assertEquals("Duplicate resource: " + resource.getClass(), ex.getMessage());
    }

    @Test
    void callerMapMutationAfterBuildDoesNotAffectBuiltManager() {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        builtins.put("bi", new TraceResource("b", new ArrayList<>()));
        ResourceManager manager = new ResourceManager.Builder().build(builtins);
        builtins.put("extra", new TraceResource("e", new ArrayList<>()));
        assertThrows(ResourceException.class, () -> manager.getBuiltinResource("extra"));
    }

    // ---------- 托管空间：仅释放、不可查询 ----------

    @Test
    void managedResourcesAreNotQueryable() {
        TraceResource resource = new TraceResource("m", new ArrayList<>());
        ResourceManager manager = new ResourceManager.Builder()
                .addManaged(resource)
                .build();
        assertThrows(ResourceException.class, () -> manager.getResource("traceResource"));
        assertThrows(ResourceException.class, () -> manager.getResource(TraceResource.class));
        assertThrows(ResourceException.class, () -> manager.getBuiltinResource("traceResource"));
    }

    @Test
    void addManagedRejectsNullResource() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new ResourceManager.Builder().addManaged(null));
        assertEquals("resource must not be null", ex.getMessage());
    }

    @Test
    void addManagedReturnsBuilderForChaining() {
        ResourceManager.Builder builder = new ResourceManager.Builder();
        assertSame(builder, builder.addManaged(new TraceResource("m", new ArrayList<>())));
    }

    @Test
    void builderRejectsSameInstanceAsCustomThenManaged() {
        TraceResource resource = new TraceResource("r", new ArrayList<>());
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addResource("a", resource);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.addManaged(resource));
        assertEquals("Duplicate resource: " + resource.getClass(), ex.getMessage());
    }

    @Test
    void builderRejectsSameInstanceAsManagedThenCustom() {
        TraceResource resource = new TraceResource("r", new ArrayList<>());
        ResourceManager.Builder builder = new ResourceManager.Builder();
        builder.addManaged(resource);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.addResource("a", resource));
        assertEquals("Duplicate resource: " + resource.getClass(), ex.getMessage());
    }

    // ---------- 统一释放：三空间全局登记顺序 ----------

    @Test
    void freeReleasesChainEntriesThenBuiltinsInGlobalRegistrationOrder() {
        List<String> trace = new ArrayList<>();
        ResourceManager manager = new ResourceManager.Builder()
                .addManaged(new TraceResource("m", trace))
                .addResource("c", new TraceResource("c", trace))
                .build(orderedBuiltins(new TraceResource("b1", trace), new TraceResource("b2", trace)));
        manager.free();
        assertEquals(List.of("m", "c", "b1", "b2"), trace);
        manager.free();
        assertEquals(List.of("m", "c", "b1", "b2"), trace);
    }

    /** 组装内置资源表：按传入顺序以 b1、b2……为键登记 */
    private static Map<String, AutoCloseable> orderedBuiltins(TraceResource... resources) {
        Map<String, AutoCloseable> builtins = new LinkedHashMap<>();
        for (int i = 0; i < resources.length; i++) {
            builtins.put("b" + (i + 1), resources[i]);
        }
        return builtins;
    }

    // ---------- ResourceProvider：只读查询视图 ----------

    @Test
    void managerIsAResourceProviderAndQueriesWorkThroughIt() {
        TraceResource builtin = new TraceResource("b", new ArrayList<>());
        TraceResource custom = new TraceResource("c", new ArrayList<>());
        ResourceProvider provider = new ResourceManager.Builder()
                .addResource("c", custom)
                .build(Map.of("b", builtin));
        assertSame(builtin, provider.getBuiltinResource("b"));
        assertSame(builtin, provider.getBuiltinResource("b", TraceResource.class));
        assertSame(custom, provider.getResource("c"));
        assertSame(custom, provider.getResource("c", TraceResource.class));
        assertSame(custom, provider.getResource(TraceResource.class));
    }

    @Test
    void resourceProviderExposesNoReleaseOperation() {
        // 钉住接口隔离口径：ResourceProvider 只收编查询，释放由 ResourceManager 独自持有
        for (Method method : ResourceProvider.class.getMethods()) {
            assertNotEquals("free", method.getName(),
                    "ResourceProvider 不得暴露释放操作");
        }
    }
}
