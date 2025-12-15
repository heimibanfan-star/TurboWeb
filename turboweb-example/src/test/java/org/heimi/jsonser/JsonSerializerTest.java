package org.heimi.jsonser;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import org.junit.jupiter.api.Test;
import top.turboweb.commons.serializer.JacksonJsonSerializer;
import top.turboweb.commons.serializer.JsonSerializer;

public class JsonSerializerTest {

    public final JsonSerializer jsonSerializer = new JacksonJsonSerializer();

    static class User {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    '}';
        }
    }

    static class Result<T> {
        T data;

        public Result(T data) {
            this.data = data;
        }

        public Result() {
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    @Test
    public void test() {
        User user = new User();
        user.setName("张三");
        user.setAge(18);
        Result<User> result = new Result<>(user);
        String json = jsonSerializer.beanToJson(result);
        System.out.println(json);
        JacksonJsonSerializer serializer = (JacksonJsonSerializer) jsonSerializer;
        Result<User> bean = serializer.jsonToBean(json, new TypeReference<>() {});
        System.out.println(bean.getData().getClass());
    }
}
