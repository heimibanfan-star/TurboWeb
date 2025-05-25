package org.example.requestexample;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UserDTO {
	@NotBlank(message = "name can not be blank")
	private String name;
	@NotNull(message = "age can not be null")
	@Min(value = 0, message = "age can not less than 0")
	@Max(value = 100, message = "age can not greater than 100")
	private Integer age;
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
	public Integer getAge() {
		return age;
	}
	@Override
	public String toString() {
		return "UserDTO{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
