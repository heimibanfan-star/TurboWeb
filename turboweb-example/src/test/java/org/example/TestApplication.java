package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO
 */
public class TestApplication {
	public static void main(String[] args) {
		Path path = Paths.get("/static/");
		Path resovlePath = path.resolve("/index.html").normalize();
		System.out.println(resovlePath);
	}
}
