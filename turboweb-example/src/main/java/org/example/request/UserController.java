package org.example.request;

import top.turboweb.anno.method.Get;
import top.turboweb.anno.method.Post;
import top.turboweb.anno.RequestPath;
import top.turboweb.http.context.HttpContext;

import java.util.List;

@RequestPath
public class UserController {

    @Get("/user01")
    public String user01(HttpContext context) {
        String name = context.query("name");
        return "name=" + name;
    }

    @Get("/user02")
    public String user02(HttpContext context) {
        List<String> names = context.queries("name");
        return "name:" + names;
    }

    @Get("/user03")
    public String user03(HttpContext context) {
        Integer age = context.queryInt("age");
        return "age:" + age;
    }

    @Get("/user04")
    public String user04(HttpContext context) {
        PageDTO pageDTO = context.loadQuery(PageDTO.class);
        return "pageNum:" + pageDTO.getPageNum() + " pageSize:" + pageDTO.getPageSize();
    }

    @Post("/user05")
    public String user05(HttpContext context) {
        UserDTO userDTO = context.loadForm(UserDTO.class);
        System.out.println(userDTO);
        return "user05";
    }

    @Post("/user06")
    public String user06(HttpContext context) {
        UserDTO userDTO = context.loadJson(UserDTO.class);
        System.out.println(userDTO);
        return "user06";
    }

    @Post("/user07")
    public String user07(HttpContext context) {
        UserDTO userDTO = context.loadValidJson(UserDTO.class);
        System.out.println(userDTO);
        throw new RuntimeException("\"hello\"");
//        return "user07";
    }

    @Post("/user08")
    public String user08(HttpContext context) {
        Student student = context.loadValidJson(Student.class, Groups.Update.class);
        System.out.println(student);
        return "user08";
    }

    @Get("/user09/{name:str}")
    public String user09(HttpContext context) {
        String name = context.param("name");
        System.out.println(name);
        return "name=" + name;
    }


}
