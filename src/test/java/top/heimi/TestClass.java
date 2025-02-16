package top.heimi;

import org.turbo.anno.*;

/**
 * TODO
 */
@RequestPath("/user")
public class TestClass {

    @Get
    public void test(@QueryParam("name") String name) {
        System.out.println(name);
    }

    @Post
    public void test2(@QueryParam("name") String name, @QueryParam("age") int age) {
        System.out.println(name + " " + age);
    }

    @Put("/user/{name}")
    public void test3(@PathParam("name") String name, @QueryParam("age") int age) {
        System.out.println(name + " " + age);
    }

//    @Get
//    public void test2(int age) {
//    }
}
