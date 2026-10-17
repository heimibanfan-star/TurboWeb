package top.turboweb.http.router;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.turboweb.http.context.FullHttpContext;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.PathParameterParser;
import top.turboweb.http.testkit.Contexts;
import top.turboweb.rest.parameter.BeanQuery;
import top.turboweb.rest.router.Get;
import top.turboweb.serializer.JacksonJsonSerializer;
import top.turboweb.validation.BeanValidator;
import top.turboweb.validation.HibernateBeanValidator;

import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RestRouteRegister 单元测试:登记后生产出的管理器可处理已登记路由、指定校验器随生产进入管理器、
 * 生产传入的序列化器经参数绑定直达绑定器、生产门闩对重复生产与生产后的每一处登记入口一律拒绝、
 * 序列化器入参判空。
 */
class RestRouteRegisterTest {

    private static final String PRODUCED_MESSAGE = "Route register is already produced";

    public static class PingController {

        @Get("/ping")
        public String ping() {
            return "pong";
        }
    }

    /** 门闩用例的登记目标：默认解析器表里没有的一个参数解析器 */
    static class ExtraParser implements ParameterParser {

        @Override
        public Binder parse(Parameter parameter) {
            return null;
        }
    }

    /** 绑定为 bean 且注解要求校验的控制器，用于证明登记表指定的校验器确实进入生产出的管理器 */
    public static class ValidatedController {

        @Get("/vb")
        public String vb(@BeanQuery(true) Vb bean) {
            return "vb:" + bean.getName();
        }
    }

    public static class Vb {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 统计 mapToBean 调用次数的序列化器夹具：证明生产注入的序列化器直达参数绑定 */
    static class CountingJsonSerializer extends JacksonJsonSerializer {

        private int mapToBeanCalls;

        @Override
        public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
            mapToBeanCalls++;
            return super.mapToBean(map, beanClass);
        }
    }

    /** 只记录调用次数的校验器夹具：用它区分指定的校验器与默认 Hibernate 引擎 */
    static class RecordingBeanValidator implements BeanValidator {

        private int calls;

        @Override
        public void validate(Object bean) {
            calls++;
        }

        @Override
        public void validate(Object bean, Class<?>... groups) {
            calls++;
        }

        @Override
        public void close() {
        }
    }

    private static HttpContext ctx(String uri) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        return new FullHttpContext(Contexts.message(request, StandardCharsets.UTF_8),
                null, null, null, StandardCharsets.UTF_8);
    }

    @Test
    void producedManagerHandlesRegisteredRoute() {
        RouteHandlerManager manager = new RestRouteRegister()
                .registerRoute(new PingController())
                .newRouteHandlerManager(new JacksonJsonSerializer());
        assertEquals("pong", manager.handle(ctx("/ping")),
                "登记的路由由生产出的管理器处理");
    }

    @Test
    void specifiedBeanValidatorValidatesBoundBean() {
        RecordingBeanValidator validator = new RecordingBeanValidator();
        RestRouteRegister register = new RestRouteRegister().registerRoute(new ValidatedController());
        assertSame(register, register.beanValidator(validator), "指定校验器返回登记表自身");
        RouteHandlerManager manager = register.newRouteHandlerManager(new JacksonJsonSerializer());

        assertEquals("vb:alice", manager.handle(ctx("/vb?name=alice")),
                "指定校验器后路由照常可用");
        assertEquals(1, validator.calls, "形参绑定为 bean 且注解要求校验时经指定的校验器执行，默认引擎不参与");
    }

    @Test
    void productionSerializerReachesParameterBinding() {
        CountingJsonSerializer serializer = new CountingJsonSerializer();
        RouteHandlerManager manager = new RestRouteRegister()
                .registerRoute(new ValidatedController())
                .newRouteHandlerManager(serializer);
        assertEquals("vb:alice", manager.handle(ctx("/vb?name=alice")),
                "以给定序列化器生产后路由照常可用");
        assertEquals(1, serializer.mapToBeanCalls,
                "生产入口传入的序列化器随参数绑定交给绑定器，bean 绑定经其 mapToBean 完成");
    }

    @Test
    void secondProductionIsRejected() {
        RestRouteRegister register = new RestRouteRegister()
                .registerRoute(new PingController());
        register.newRouteHandlerManager(new JacksonJsonSerializer());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> register.newRouteHandlerManager(new JacksonJsonSerializer()),
                "登记表生产一次即冻结,重复生产拒绝");
        assertEquals(PRODUCED_MESSAGE, error.getMessage());
    }

    @Test
    void registrationAfterProductionIsRejected() {
        RestRouteRegister register = new RestRouteRegister()
                .registerRoute(new PingController());
        register.newRouteHandlerManager(new JacksonJsonSerializer());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> register.registerRoute(new PingController()),
                "生产之后再登记的路由进不了已冻结的管理器,直接拒绝而非静默丢弃");
        assertEquals(PRODUCED_MESSAGE, error.getMessage());
    }

    @Test
    void everyRegistrationEntryIsRejectedAfterProduction() {
        RestRouteRegister register = new RestRouteRegister()
                .registerRoute(new PingController());
        register.newRouteHandlerManager(new JacksonJsonSerializer());

        Map<String, Executable> entries = new LinkedHashMap<>();
        entries.put("registerRoute(实例)", () -> register.registerRoute(new PingController()));
        entries.put("registerRoute(类,实例)", () -> register.registerRoute(PingController.class, new PingController()));
        entries.put("registerRoute(列表)", () -> register.registerRoute(List.<Object>of(new PingController())));
        entries.put("registerRoute(映射)", () -> register.registerRoute(
                Map.<Class<?>, Object>of(PingController.class, new PingController())));
        entries.put("beanValidator", () -> register.beanValidator(new HibernateBeanValidator()));
        entries.put("registerParameterParserFirst", () -> register.registerParameterParserFirst(new ExtraParser()));
        entries.put("registerParameterParserLast", () -> register.registerParameterParserLast(new ExtraParser()));
        entries.put("registerParameterParserBefore",
                () -> register.registerParameterParserBefore(new PathParameterParser(), new ExtraParser()));
        entries.put("registerParameterParserAfter",
                () -> register.registerParameterParserAfter(new PathParameterParser(), new ExtraParser()));
        entries.put("registerParameterParser(替换)",
                () -> register.registerParameterParser(new PathParameterParser(), new ExtraParser()));
        entries.put("removeParameterParser", () -> register.removeParameterParser(new ExtraParser()));

        for (Map.Entry<String, Executable> entry : entries.entrySet()) {
            IllegalStateException error = assertThrows(IllegalStateException.class, entry.getValue(),
                    "生产之后 " + entry.getKey() + " 不得再改动登记表");
            assertEquals(PRODUCED_MESSAGE, error.getMessage(), entry.getKey() + " 的拒绝出自生产门闩");
        }
    }

    @Test
    void nullSerializerIsRejected() {
        RestRouteRegister register = new RestRouteRegister()
                .registerRoute(new PingController());
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> register.newRouteHandlerManager(null));
        assertEquals("jsonSerializer must not be null", error.getMessage());
    }
}
