package org.example.exception;


import top.turboweb.rest.route.Get;
import top.turboweb.rest.route.Post;
import top.turboweb.rest.route.Route;
import top.turboweb.http.context.HttpContext;

@Route("/excep")
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
