package top.turboweb.http.router;

import org.junit.jupiter.api.Test;
import top.turboweb.http.router.parameter.BeanFormParameterParser;
import top.turboweb.http.router.parameter.ParameterParser.Binder;
import top.turboweb.http.router.parameter.InternTypeParameterParser;
import top.turboweb.http.router.parameter.BeanJsonParameterParser;
import top.turboweb.http.router.parameter.ParameterParser;
import top.turboweb.http.router.parameter.BeanQueryParameterParser;
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
 * RestRouterHandlerManager.Builder 参数解析器注册管理单元测试:
 * 默认解析器次序、开头/结尾/目标前/目标后/原位替换五个注册口、按 class 剔除,
 * 以及同类查重、判空与目标缺失的失败快抛语义。
 */
class RestRouterHandlerManagerBuilderParserTest {

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
    private static List<ParameterParser> parsersOf(RestRouterHandlerManager.Builder builder) throws Exception {
        Field field = RestRouterHandlerManager.Builder.class.getDeclaredField("parsers");
        field.setAccessible(true);
        return (List<ParameterParser>) field.get(builder);
    }

    private static List<String> classNamesOf(RestRouterHandlerManager.Builder builder) throws Exception {
        return parsersOf(builder).stream().map(parser -> parser.getClass().getSimpleName()).toList();
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
                BeanJsonParameterParser.class.getSimpleName());
        assertEquals(expected, classNamesOf(new RestRouterHandlerManager.Builder()), "默认七个解析器按注册顺序排列");
    }

    @Test
    void registerFirstPutsParserAtHead() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        builder.registerParameterParserFirst(alpha);
        List<ParameterParser> parsers = parsersOf(builder);
        assertSame(alpha, parsers.get(0), "开头添加落在表头");
        assertEquals(8, parsers.size());
    }

    @Test
    void registerLastAppendsParserAfterDefaults() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        BetaParser beta = new BetaParser();
        builder.registerParameterParserLast(beta);
        List<ParameterParser> parsers = parsersOf(builder);
        assertSame(beta, parsers.get(parsers.size() - 1), "结尾添加落在表尾,默认解析器之后");
        assertEquals(8, parsers.size());
    }

    @Test
    void registerBeforeLocatesTargetByClass() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        builder.registerParameterParserBefore(new BeanJsonParameterParser(), alpha);
        List<String> names = classNamesOf(builder);
        assertEquals(8, names.size());
        int alphaIndex = names.indexOf("AlphaParser");
        assertEquals(names.indexOf("BeanJsonParameterParser") - 1, alphaIndex, "传入同类新实例即可定位默认目标,插在其前");
    }

    @Test
    void registerAfterLocatesTargetByClass() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        BetaParser beta = new BetaParser();
        builder.registerParameterParserAfter(new InternTypeParameterParser(), beta);
        List<String> names = classNamesOf(builder);
        assertEquals(1, names.indexOf("BetaParser"), "插到表首解析器之后");
        assertEquals("PathParameterParser", names.get(2));
    }

    @Test
    void registerAtReplacesTargetKeepingPosition() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        builder.registerParameterParser(new UploadParameterParser(), alpha);
        List<String> names = classNamesOf(builder);
        assertEquals(7, names.size(), "替换不改变解析器总数");
        assertEquals("AlphaParser", names.get(3), "替换发生在目标原位置");
        assertEquals(List.of("InternTypeParameterParser", "PathParameterParser", "QueryParameterParser"),
                names.subList(0, 3), "目标之前的次序不动");
        assertEquals(List.of("BeanQueryParameterParser", "BeanFormParameterParser", "BeanJsonParameterParser"),
                names.subList(4, 7), "目标之后的次序不动");
    }

    @Test
    void missingTargetThrowsOnBeforeAfterAndAt() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserBefore(new AlphaParser(), new BetaParser()), "目标未注册时 before 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserAfter(new AlphaParser(), new BetaParser()), "目标未注册时 after 拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParser(new AlphaParser(), new BetaParser()), "目标未注册时替换拒绝");
    }

    @Test
    void duplicateClassRejectedOnAllRegisterPaths() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        IllegalArgumentException firstError = assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserFirst(new QueryParameterParser()), "first 拒同类重复");
        assertEquals(QueryParameterParser.class + " is already registered", firstError.getMessage(),
                "重复注册消息在类名与文案之间留空格");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserLast(new QueryParameterParser()), "last 拒同类重复");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserBefore(new PathParameterParser(), new QueryParameterParser()),
                "before 拒同类重复");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParserAfter(new PathParameterParser(), new QueryParameterParser()),
                "after 拒同类重复");
        assertThrows(IllegalArgumentException.class,
                () -> builder.registerParameterParser(new PathParameterParser(), new QueryParameterParser()),
                "替换拒同类重复");
    }

    @Test
    void nullParserRejectedOnRegisterPaths() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        assertThrows(NullPointerException.class, () -> builder.registerParameterParserFirst(null));
        assertThrows(NullPointerException.class, () -> builder.registerParameterParserLast(null));
        assertThrows(NullPointerException.class,
                () -> builder.registerParameterParserBefore(new PathParameterParser(), null));
        assertThrows(NullPointerException.class,
                () -> builder.registerParameterParserAfter(new PathParameterParser(), null));
        assertThrows(NullPointerException.class,
                () -> builder.registerParameterParser(new PathParameterParser(), null));
    }

    @Test
    void nullTargetRejectedOnPositionedPaths() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        NullPointerException beforeError = assertThrows(NullPointerException.class,
                () -> builder.registerParameterParserBefore(null, new AlphaParser()));
        assertEquals("目标解析器不能为空", beforeError.getMessage());
        assertThrows(NullPointerException.class,
                () -> builder.registerParameterParserAfter(null, new AlphaParser()));
        assertThrows(NullPointerException.class,
                () -> builder.registerParameterParser(null, new AlphaParser()));
    }

    @Test
    void removeDropsRegisteredInstanceOnly() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        BetaParser beta = new BetaParser();
        builder.registerParameterParserLast(alpha);
        builder.registerParameterParserLast(beta);
        assertEquals(9, parsersOf(builder).size());
        builder.removeParameterParser(alpha, beta, new PathParameterParser(), null);
        List<ParameterParser> parsers = parsersOf(builder);
        assertEquals(6, parsers.size(), "按 class 剔除,同类的 Rest 默认解析器一并移除,null 参数无副作用");
        assertTrue(parsers.stream().noneMatch(parser -> parser instanceof AlphaParser || parser instanceof BetaParser));
        assertTrue(parsers.stream().noneMatch(parser -> parser instanceof PathParameterParser));
    }

    @Test
    void registrationMethodsReturnSameBuilder() {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        BetaParser beta = new BetaParser();
        assertSame(builder, builder.registerParameterParserFirst(alpha));
        assertSame(builder, builder.registerParameterParserLast(beta));
        assertSame(builder, builder.registerParameterParserBefore(new AlphaParser(), new GammaParser()));
        assertSame(builder, builder.registerParameterParserAfter(new BetaParser(), new DeltaParser()));
        assertSame(builder, builder.registerParameterParser(new GammaParser(), new EpsilonParser()));
        assertSame(builder, builder.removeParameterParser());
    }

    @Test
    void registeredInstanceIsKeptNotRecreated() throws Exception {
        RestRouterHandlerManager.Builder builder = new RestRouterHandlerManager.Builder();
        AlphaParser alpha = new AlphaParser();
        builder.registerParameterParserFirst(alpha);
        assertSame(alpha, parsersOf(builder).get(0), "入表的是调用方实例本身");
        assertNotNull(builder);
    }
}
