package org.example.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class Student {

    @NotNull(message = "id不能为空", groups = Groups.Update.class)
    private Long id;
    @NotBlank(message = "name不能为空", groups = {Groups.Add.class, Groups.Update.class})
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Student{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
