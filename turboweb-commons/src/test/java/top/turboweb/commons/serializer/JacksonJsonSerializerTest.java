package top.turboweb.commons.serializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.exception.TurboSerializableException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson 序列化器：四个构造器的装配差异 + 十一个方法的往返与异常包装。
 */
class JacksonJsonSerializerTest {

    /** 固定时刻，避免用 now() 让断言依赖运行时间。 */
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 6, 12, 34, 56);

    public static class Profile {
        private String name;
        private Integer age;

        public Profile() {
        }

        public Profile(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Profile p && Objects.equals(name, p.name) && Objects.equals(age, p.age);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    public static class TimeHolder {
        private LocalDateTime dateTime;
        private LocalDate date;
        private LocalTime time;

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public LocalTime getTime() {
            return time;
        }

        public void setTime(LocalTime time) {
            this.time = time;
        }
    }

    private static JacksonJsonSerializer withJavaTime() {
        return new JacksonJsonSerializer();
    }

    @Test
    void objectMapperCtorIsUsedAsIsWithoutJavaTime() {
        JacksonJsonSerializer s = new JacksonJsonSerializer(new ObjectMapper());
        assertThrows(TurboSerializableException.class, () -> s.beanToJson(AT),
                "裸 ObjectMapper 没装 JavaTimeModule，序列化 LocalDateTime 必须失败并被包装");
    }

    @Test
    void moduleVarargsCtorRegistersModuleButStillHasNoJavaTime() {
        JacksonJsonSerializer s = new JacksonJsonSerializer(new SimpleModule());
        assertThrows(TurboSerializableException.class, () -> s.beanToJson(AT),
                "只 registerModules(用户模块) 不会带来时间支持");
        // 但未知字段容忍是开着的：这条同时把 :55 钉住
        Profile p = s.jsonToBean("{\"name\":\"n\",\"age\":1,\"unknown\":true}", Profile.class);
        assertEquals("n", p.getName());
    }

    @Test
    void javaTimeFormatsArePinned() {
        JacksonJsonSerializer s = withJavaTime();
        TimeHolder t = new TimeHolder();
        t.setDateTime(AT);
        t.setDate(AT.toLocalDate());
        t.setTime(AT.toLocalTime());
        assertEquals("{\"dateTime\":\"2026-10-06 12:34:56\",\"date\":\"2026-10-06\",\"time\":\"12:34:56\"}",
                s.beanToJson(t));
        TimeHolder back = s.jsonToBean(
                "{\"dateTime\":\"2026-10-06 12:34:56\",\"date\":\"2026-10-06\",\"time\":\"12:34:56\"}",
                TimeHolder.class);
        assertEquals(AT, back.getDateTime());
        assertEquals(AT.toLocalDate(), back.getDate());
        assertEquals(AT.toLocalTime(), back.getTime());
    }

    @Test
    void defaultCtorToleratesUnknownPropertiesAndHandlesTime() {
        // 无参构造器 = this(objMap -> {})：既带 JavaTime（裸 LocalDateTime 序列化成带引号的时间串），
        // 也带未知字段容忍
        JacksonJsonSerializer s = withJavaTime();
        assertEquals("\"2026-10-06 12:34:56\"", s.beanToJson(AT));
        Profile p = s.jsonToBean("{\"name\":\"n\",\"age\":1,\"unknown\":true}", Profile.class);
        assertEquals(new Profile("n", 1), p);
    }

    @Test
    void consumerCtorRunsConfigurationBeforeUnknownFieldTolerance() {
        ObjectMapper[] seen = new ObjectMapper[1];
        JacksonJsonSerializer s = new JacksonJsonSerializer(m -> seen[0] = m);
        assertSame(s.objectMapper(), seen[0], "config.accept 收到的必须就是最终持有的 mapper");
        Profile p = s.jsonToBean("{\"name\":\"n\",\"age\":1,\"unknown\":true}", Profile.class);
        assertEquals(1, p.getAge());
    }

    @Test
    void mapAndBeanRoundTrip() {
        JacksonJsonSerializer s = withJavaTime();
        Map<String, Object> map = Map.of("name", "n", "age", 7);
        Profile p = s.mapToBean(map, Profile.class);
        assertEquals("n", p.getName());
        assertEquals(7, p.getAge());
        Map<?, ?> back = s.beanToMap(p);
        assertEquals("n", back.get("name"));
        assertEquals(7, back.get("age"));
    }

    @Test
    void jsonToMapAndJsonToList() {
        JacksonJsonSerializer s = withJavaTime();
        Map<?, ?> m = s.jsonToMap("{\"name\":\"n\",\"age\":7}");
        assertEquals("n", m.get("name"));
        List<Profile> list = s.jsonToList("[{\"name\":\"a\",\"age\":1},{\"name\":\"b\",\"age\":2}]", Profile.class);
        assertEquals(List.of(new Profile("a", 1), new Profile("b", 2)), list);
    }

    @Test
    void typeReferenceAndJavaTypeOverloads() {
        JacksonJsonSerializer s = withJavaTime();
        List<Profile> viaRef = s.jsonToBean("[{\"name\":\"a\",\"age\":1}]",
                new TypeReference<List<Profile>>() {
                });
        assertEquals(1, viaRef.size());
        Profile viaJavaType = s.jsonToBean("{\"name\":\"b\",\"age\":2}",
                s.objectMapper().getTypeFactory().constructType(Profile.class));
        assertEquals("b", viaJavaType.getName());
    }

    @Test
    void deepinCloneReturnsADistinctEquivalentInstance() {
        JacksonJsonSerializer s = withJavaTime();
        Profile origin = new Profile("n", 7);
        Profile clone = s.deepinClone(origin);
        assertNotSame(origin, clone);
        assertEquals(origin, clone);
        clone.setAge(8);
        assertEquals(7, origin.getAge(), "克隆体改名不能影响原件");
    }

    @Test
    void jsonUpdateBeanMutatesInPlaceAndReturnsSameInstance() {
        JacksonJsonSerializer s = withJavaTime();
        Profile bean = new Profile("old", 1);
        Profile returned = s.jsonUpdateBean("{\"name\":\"new\"}", bean);
        assertSame(bean, returned);
        assertEquals("new", bean.getName());
        assertEquals(1, bean.getAge(), "json 未给出的字段保持原值");
    }

    @Test
    void malformedJsonIsWrappedForEveryReadPath() {
        JacksonJsonSerializer s = withJavaTime();
        assertThrows(TurboSerializableException.class, () -> s.jsonToBean("not json", Profile.class));
        assertThrows(TurboSerializableException.class, () -> s.jsonToList("{not an array}", Profile.class));
        assertThrows(TurboSerializableException.class, () -> s.jsonToMap("[]"));
        assertThrows(TurboSerializableException.class, () -> s.jsonUpdateBean("not json", new Profile("n", 1)));
        // TypeReference / JavaType 两个重载也是读路径，包装行为必须一致（:170-:172 / :185-:187）
        assertThrows(TurboSerializableException.class,
                () -> s.jsonToBean("not json", new TypeReference<List<Profile>>() {
                }));
        assertThrows(TurboSerializableException.class,
                () -> s.jsonToBean("not json",
                        s.objectMapper().getTypeFactory().constructType(Profile.class)));
    }

    @Test
    @DisplayName("钉住 F17：convertValue 一路（mapToBean/beanToMap）不包装异常，与其余方法不对称")
    void convertValueFailuresAreNotWrapped() {
        // 钉住 F17：若将来修正，本用例应改为期望正确行为
        JacksonJsonSerializer s = withJavaTime();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> s.mapToBean(Map.of("name", "n", "age", "not-a-number"), Profile.class));
        assertFalse(TurboSerializableException.class.isAssignableFrom(e.getClass()),
                "产品代码只在 readValue/writeValue 路径包 TurboSerializableException");
        assertTrue(e.getCause() != null, "convertValue 把底层异常挂在 cause 上，调用方仍可取原因");
    }
}
