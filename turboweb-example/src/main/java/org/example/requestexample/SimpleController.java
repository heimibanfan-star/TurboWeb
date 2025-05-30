package org.example.requestexample;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

import java.util.List;

@RequestPath
public class SimpleController {

    @Get("/simple01")
    public String simple01(HttpContext c) {
        String name = c.query("name");
        return "name:" + name;
    }

    @Get("/simple02")
    public String simple02(HttpContext c) {
        String name = c.query("name", "turboweb");
        return "name:" + name;
    }

    @Get("/simple03")
    public String simple03(HttpContext c) {
        List<String> names = c.queries("name");
        return "name:" + names;
    }

    @Get("/simple04")
    public String simple04(HttpContext c) {
        Integer age = c.queryInt("age");
        return "age:" + age;
    }

    @Get("/simple05")
    public String simple05(HttpContext c) {
        Integer age = c.queryInt("age", 18);
        return "age:" + age;
    }

    @Get("/simple06")
    public String simple06(HttpContext c) {
        List<Integer> grades = c.queriesInt("grade");
        return "grade:" + grades;
    }
}
