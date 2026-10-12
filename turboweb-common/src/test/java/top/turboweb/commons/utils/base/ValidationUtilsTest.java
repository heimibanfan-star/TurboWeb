package top.turboweb.commons.utils.base;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验工具：默认组与指定组分别取证（消息文本是返回值，不是日志）。
 */
class ValidationUtilsTest {

    interface OnCreate {
    }

    public static class Form {
        @NotBlank(message = "name 不能为空")
        private String name;

        @Min(value = 18, message = "age 至少 18", groups = OnCreate.class)
        private Integer age;

        public Form(String name, Integer age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void validObjectYieldsNoMessages() {
        assertTrue(ValidationUtils.validate(new Form("n", 20)).isEmpty());
    }

    @Test
    void defaultMessageIsReturned() {
        List<String> msgs = ValidationUtils.validate(new Form("", 20));
        assertEquals(List.of("name 不能为空"), msgs);
    }

    @Test
    void groupNarrowsTheChecks() {
        // 默认组不管 groups=OnCreate 的 @Min
        assertTrue(ValidationUtils.validate(new Form("n", 5)).isEmpty());
        assertEquals(List.of("age 至少 18"), ValidationUtils.validate(new Form("n", 5), OnCreate.class));
    }

}
