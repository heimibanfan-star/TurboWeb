package top.heimi.pojos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * TODO
 */
public class User {
	@NotBlank(message = "name不能为空")
	private String name;
	@NotNull(message = "age不能为空")
	@Min(value = 0, message = "age不能小于0")
	@Max(value = 200, message = "age不能大于200")
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

	@Override
	public String toString() {
		return "User{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
