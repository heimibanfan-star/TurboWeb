package top.turboweb.http.router;

import org.junit.jupiter.api.Test;
import top.turboweb.http.router.parameter.BeanFormParameterParser;
import top.turboweb.http.router.parameter.ParameterParser.Binder;
import top.turboweb.http.router.parameter.InternTypeParameterParser;
import top.turboweb.http.router.parameter.BeanJsonParameterParser;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.BeanQueryParameterParser;
import top.turboweb.http.router.parameter.CookieParameterParser;
import top.turboweb.http.router.parameter.FormParameterParser;
import top.turboweb.http.router.parameter.HeaderParameterParser;
import top.turboweb.http.router.parameter.QueryParameterParser;
import top.turboweb.http.router.parameter.PathParameterParser;
import top.turboweb.http.router.parameter.UploadParameterParser;

import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RestRouteRegister 参数解析器注册管理单元测试:
 * 默认解析器次序、开头/结尾/目标前/目标后/原位替换五个注册口、按 class 剔除,
 * 以及同类查重、判空与目标缺失的失败快抛语义。
 */
class RestRouteRegisterParserTest {

    static class AlphaParser implements ParameterParser {

        @Override
        public ParameterParser.Binder parse(Parameter parameter) {
            return null;
        }
    }

    static class BetaParser implements ParameterParser {

        @Override
        public ParameterParser.Binder parse(Parameter parameter) {
            return null;
        }
    }

    static class GammaParser implements ParameterParser {

        @Override
        public ParameterParser.Binder parse(Parameter parameter) {
            return null;
        }
    }

    static class DeltaParser implements ParameterParser {

        @Override
        public ParameterParser.Binder parse(Parameter parameter) {
            return null;
        }
    }

    static class EpsilonParser implements ParameterParser {

        @Override
        public ParameterParser.Binder parse(Parameter parameter) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ParameterParser> parsersOf(RestRouteRegister register) throws Exception {
        Field field = RestRouteRegister.class.getDeclaredField("parsers");
        field.setAccessible(true);
        return (List<ParameterParser>) field.get(register);
    }

    private static List<String> classNamesOf(RestRouteRegister register) throws Exception {
        return parsersOf(register).stream().map(parser -> parser.getClass().getSimpleName()).toList();
    }

    @Test
    void defaultParsersAreOrderedByRegistration() throws Exception {
        List<String> expected = List.of(
                InternTypeParameterParser.class.getSimpleName(),
                PathParameterParser.class.getSimpleName(),
                QueryParameterParser.class.getSimpleName(),
                UploadParameterParser.class.getSimpleName(),
                BeanQueryParameterParser.class.getSimpleName(),
                BeanFormParameterParser.class.getSimpleName(),
                BeanJsonParameterParser.class.getSimpleName(),
                CookieParameterParser.class.getSimpleName(),
                FormParameterParser.class.getSimpleName(),
                HeaderParameterParser.class.getSimpleName());
        assertEquals(expected, classNamesOf(new RestRouteRegister()), "默认十个解析器按注册顺序排列");
    }

    @Test
    void registerFirstPutsParserAtHead() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        register.registerParameterParserFirst(alpha);
        List<ParameterParser> parsers = parsersOf(register);
        assertSame(alpha, parsers.get(0), "开头添加落在表头");
        assertEquals(11, parsers.size());
    }

    @Test
    void registerLastAppendsParserAfterDefaults() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        BetaParser beta = new BetaParser();
        register.registerParameterParserLast(beta);
        List<ParameterParser> parsers = parsersOf(register);
        assertSame(beta, parsers.get(parsers.size() - 1), "结尾添加落在表尾,默认解析器之后");
        assertEquals(11, parsers.size());
    }

    @Test
    void registerBeforeLocatesTargetByClass() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        register.registerParameterParserBefore(new BeanJsonParameterParser(), alpha);
        List<String> names = classNamesOf(register);
        assertEquals(11, names.size());
        int alphaIndex = names.indexOf("AlphaParser");
        assertEquals(names.indexOf("BeanJsonParameterParser") - 1, alphaIndex, "传入同类新实例即可定位默认目标,插在其前");
    }

    @Test
    void registerAfterLocatesTargetByClass() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        BetaParser beta = new BetaParser();
        register.registerParameterParserAfter(new InternTypeParameterParser(), beta);
        List<String> names = classNamesOf(register);
        assertEquals(1, names.indexOf("BetaParser"), "插到表首解析器之后");
        assertEquals("PathParameterParser", names.get(2));
    }

    @Test
    void registerAtReplacesTargetKeepingPosition() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        register.registerParameterParser(new UploadParameterParser(), alpha);
        List<String> names = classNamesOf(register);
        assertEquals(10, names.size(), "替换不改变解析器总数");
        assertEquals("AlphaParser", names.get(3), "替换发生在目标原位置");
        assertEquals(List.of("InternTypeParameterParser", "PathParameterParser", "QueryParameterParser"),
                names.subList(0, 3), "目标之前的次序不动");
        assertEquals(List.of("BeanQueryParameterParser", "BeanFormParameterParser", "BeanJsonParameterParser",
                        "CookieParameterParser", "FormParameterParser", "HeaderParameterParser"),
                names.subList(4, 10), "目标之后的次序不动");
    }

    @Test
    void missingTargetThrowsOnBeforeAfterAndAt() {
        RestRouteRegister register = new RestRouteRegister();
        assertThrows(IllegalArgumentException.class,
                () -> register.registerParameterParserBefore(new AlphaParser(), new BetaParser()), "目标未注册时 before 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> register.registerParameterParserAfter(new AlphaParser(), new BetaParser()), "目标未注册时 after 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> register.registerParameterParser(new AlphaParser(), new BetaParser()), "目标未注册时替换拒绝");
    }

    @Test
    void duplicateClassRejectedOnAllRegisterPaths() {
        RestRouteRegister register = new RestRouteRegister();
        IllegalStateException firstError = assertThrows(IllegalStateException.class,
                () -> register.registerParameterParserFirst(new QueryParameterParser()), "first 拒同类重复");
        assertEquals(QueryParameterParser.class + " is already registered", firstError.getMessage(),
                "重复注册消息在类名与文案之间留空格");
        assertThrows(IllegalStateException.class,
                () -> register.registerParameterParserLast(new QueryParameterParser()), "last 拒同类重复");
        assertThrows(IllegalStateException.class,
                () -> register.registerParameterParserBefore(new PathParameterParser(), new QueryParameterParser()),
                "before 拒同类重复");
        assertThrows(IllegalStateException.class,
                () -> register.registerParameterParserAfter(new PathParameterParser(), new QueryParameterParser()),
                "after 拒同类重复");
        assertThrows(IllegalStateException.class,
                () -> register.registerParameterParser(new PathParameterParser(), new QueryParameterParser()),
                "替换拒同类重复");
    }

    @Test
    void nullParserRejectedOnRegisterPaths() {
        RestRouteRegister register = new RestRouteRegister();
        assertThrows(NullPointerException.class, () -> register.registerParameterParserFirst(null));
        assertThrows(NullPointerException.class, () -> register.registerParameterParserLast(null));
        assertThrows(NullPointerException.class,
                () -> register.registerParameterParserBefore(new PathParameterParser(), null));
        assertThrows(NullPointerException.class,
                () -> register.registerParameterParserAfter(new PathParameterParser(), null));
        assertThrows(NullPointerException.class,
                () -> register.registerParameterParser(new PathParameterParser(), null));
    }

    @Test
    void nullTargetRejectedOnPositionedPaths() {
        RestRouteRegister register = new RestRouteRegister();
        NullPointerException beforeError = assertThrows(NullPointerException.class,
                () -> register.registerParameterParserBefore(null, new AlphaParser()));
        assertEquals("The target parser must not be null", beforeError.getMessage());
        assertThrows(NullPointerException.class,
                () -> register.registerParameterParserAfter(null, new AlphaParser()));
        assertThrows(NullPointerException.class,
                () -> register.registerParameterParser(null, new AlphaParser()));
    }

    @Test
    void removeDropsRegisteredInstanceOnly() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        BetaParser beta = new BetaParser();
        register.registerParameterParserLast(alpha);
        register.registerParameterParserLast(beta);
        assertEquals(12, parsersOf(register).size());
        register.removeParameterParser(alpha, beta, new PathParameterParser(), null);
        List<ParameterParser> parsers = parsersOf(register);
        assertEquals(9, parsers.size(), "按 class 剔除,同类的 Rest 默认解析器一并移除,null 参数无副作用");
        assertTrue(parsers.stream().noneMatch(parser -> parser instanceof AlphaParser || parser instanceof BetaParser));
        assertTrue(parsers.stream().noneMatch(parser -> parser instanceof PathParameterParser));
    }

    @Test
    void registrationMethodsReturnSameRegister() {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        BetaParser beta = new BetaParser();
        assertSame(register, register.registerParameterParserFirst(alpha));
        assertSame(register, register.registerParameterParserLast(beta));
        assertSame(register, register.registerParameterParserBefore(new AlphaParser(), new GammaParser()));
        assertSame(register, register.registerParameterParserAfter(new BetaParser(), new DeltaParser()));
        assertSame(register, register.registerParameterParser(new GammaParser(), new EpsilonParser()));
        assertSame(register, register.removeParameterParser());
    }

    @Test
    void registeredInstanceIsKeptNotRecreated() throws Exception {
        RestRouteRegister register = new RestRouteRegister();
        AlphaParser alpha = new AlphaParser();
        register.registerParameterParserFirst(alpha);
        assertSame(alpha, parsersOf(register).get(0), "入表的是调用方实例本身");
        assertNotNull(register);
    }
}
