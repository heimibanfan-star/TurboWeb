package org.example.exception;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

@RequestPath("/excep")
public class ExcepController {
    @Get
    public String excep(HttpContext context) {
        throw new RuntimeException("excep");
    }

    @Post("/stu")
    public String stu(HttpContext context) {
        Student student = context.loadValidJson(Student.class);
        System.out.println(student);
        return "stu";
    }
}
