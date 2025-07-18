package org.example.exception;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class Student {
    @NotBlank(message = "name can not be null")
    private String name;
    @NotNull(message = "age can not be null")
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
