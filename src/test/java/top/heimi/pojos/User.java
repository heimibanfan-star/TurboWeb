package top.heimi.pojos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * TODO
 */
public class User {
    @NotBlank(message = "name不能为空")
    private String name;
    @NotNull(message = "age不能为空")
    private Integer age;

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
}
