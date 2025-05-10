package top.heimi.pojos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * TODO
 */
public class User {

	@NotBlank(message = "name is not blank")
	private String name;
	@NotNull(message = "age is not null")
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
		return "User{" +
			"name='" + name + '\'' +
			", age=" + age +
			'}';
	}
}
