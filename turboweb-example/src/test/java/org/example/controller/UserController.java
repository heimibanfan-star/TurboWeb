package org.example.controller;

import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.response.AsyncFileResponse;
import top.turboweb.http.response.HttpFileResult;
import top.turboweb.http.response.HttpResult;

import java.io.File;
import java.time.LocalDate;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

    public static class User {
        public String name;
        public int age;

        public User() {
        }

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setAge(int age) {
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

    @Post
    public String hello(HttpContext context) {
        User user = context.loadJson(User.class);
        System.out.println(user);
        return "Hello User";
    }
}
