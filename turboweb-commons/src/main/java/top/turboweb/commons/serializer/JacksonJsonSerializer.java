package top.turboweb.commons.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import top.turboweb.commons.exception.TurboSerializableException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 基于jackson的json序列化器
 */
public class JacksonJsonSerializer implements JsonSerializer {

    private final ObjectMapper objectMapper;

    /**
     * 创建一个基于jackson的json序列化器
     *
     * @param modules jackson的模块
     */
    public JacksonJsonSerializer(Module... modules) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(modules);
        this.objectMapper = objectMapper;
    }

    public JacksonJsonSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // LocalDateTime
        DateTimeFormatter localDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(localDateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(localDateTimeFormatter));

        // LocalDate
        DateTimeFormatter localDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(localDateFormatter));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(localDateFormatter));

        // LocalTime
        DateTimeFormatter localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(localTimeFormatter));
        javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(localTimeFormatter));

        // Instant (通常用于时间戳，采用 ISO 8601 格式)
        javaTimeModule.addSerializer(Instant.class, InstantSerializer.INSTANCE);
        javaTimeModule.addDeserializer(Instant.class, InstantDeserializer.INSTANT);
        objectMapper.registerModule(javaTimeModule);

        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T mapToBean(Map<String, Object> map, Class<T> beanClass) {
        return objectMapper.convertValue(map, beanClass);
    }

    @Override
    public Map<?, ?> beanToMap(Object bean) {
        return objectMapper.convertValue(bean, Map.class);
    }

    @Override
    public String beanToJson(Object bean) {
        try {
            return objectMapper.writeValueAsString(bean);
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }

    @Override
    public <T> T jsonToBean(String json, Class<T> beanClass) {
        try {
            return objectMapper.readValue(json, beanClass);
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }

    @Override
    public Map<?, ?> jsonToMap(String json) {
        return jsonToBean(json, Map.class);
    }

    @Override
    public <T> List<T> jsonToList(String json, Class<T> beanClass) {
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, beanClass)
            );
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }

    @Override
    public <T> T deepinClone(T bean) {
        String json = beanToJson(bean);
        @SuppressWarnings("unchecked")
        Class<T> beanClass = (Class<T>) bean.getClass();
        return jsonToBean(json, beanClass);
    }

    @Override
    public <T> T jsonUpdateBean(String json, T bean) {
        try {
            return objectMapper.readerForUpdating(bean).readValue(json);
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e);
        }
    }
}
