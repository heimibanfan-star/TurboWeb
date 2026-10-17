package org.example.file;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.rest.router.Get;
import top.turboweb.rest.router.Post;
import top.turboweb.rest.router.Route;
import top.turboweb.rest.parameter.UploadParam;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.core.response.AsyncFileResponse;
import top.turboweb.http.core.response.FileStreamResponse;
import top.turboweb.http.core.response.HttpFileResult;
import top.turboweb.http.core.response.ZeroCopyResponse;

import java.io.*;
import java.util.List;
import java.util.UUID;

@Route("/file")
public class FileController {

    @Post("/upload")
    public String upload01(HttpContext context) throws IOException {
        List<FileUpload> fileUploads = context.loadFiles("file");
        fileUploads.forEach(fileUpload -> {
            try {
                fileUpload.renameTo(File.createTempFile(UUID.randomUUID().toString(), ".tmp"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return "upload";
    }

    @Post("/upload2")
    public String upload02(@UploadParam("file") FileUpload file) {
        System.out.println(file);
        return "upload";
    }

    @Post("/upload3")
    public String upload03(@UploadParam("file") List<FileUpload> files) {
        files.forEach(System.out::println);
        return "upload";
    }

    @Get("/download1")
    public HttpFileResult download01(HttpContext context) throws IOException {
        File file = new File("E:\\tmp\\logo.png");
        InputStream is = new FileInputStream(file);
        byte[] bytes = is.readAllBytes();
        return HttpFileResult.bytes(bytes, "123.png");
    }

    @Get("/download2")
    public HttpResponse download02(HttpContext context) {
        File file = new File("E:\\tmp\\logo.png");
        return new FileStreamResponse(file, 8192);
    }

    @Get("/download3")
    public HttpResponse download03(HttpContext context) {
        File file = new File("E:\\tmp\\logo.png");
        return new AsyncFileResponse(file, 8192);
    }

    @Get("/download4")
    public HttpResponse download04(HttpContext context) {
        File file = new File("E:\\tmp\\logo.png");
        return new ZeroCopyResponse(file);
    }
}
