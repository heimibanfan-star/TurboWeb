package org.example.fileexample;

import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.multipart.FileUpload;
import top.turboweb.commons.anno.Get;
import top.turboweb.commons.anno.Post;
import top.turboweb.commons.anno.RequestPath;
import top.turboweb.http.context.HttpContext;
import top.turboweb.http.context.HttpContextFileHelper;
import top.turboweb.http.response.FileStreamResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

@RequestPath
public class FileController {

	@Post("/upload01")
	public String upload01(HttpContext c) throws IOException {
		FileUpload fileUpload = c.loadFile("file");
		System.out.println(fileUpload);
		fileUpload.renameTo(File.createTempFile("HeiMi", ".tmp"));
		return "upload01";
	}

	@Post("/upload02")
	public String upload02(HttpContext c) throws IOException {
		List<FileUpload> fileUploads = c.loadFiles("file");
		for (FileUpload fileUpload : fileUploads) {
			System.out.println(fileUpload);
			fileUpload.renameTo(File.createTempFile("HeiMi", ".tmp"));
		}
		return "upload02";
	}

	@Get("/download01")
	public void download01(HttpContext c) {
		File file = new File("E:\\tmp\\turbo.hprof");
		c.download(file);
	}

	@Get("/download02")
	public void download02(HttpContext c) throws FileNotFoundException {
		HttpContextFileHelper helper = c.fileHelper();
		helper.png(new FileInputStream("E:\\tmp\\logo.png"));
	}

	@Get("/download03")
	public HttpResponse download03(HttpContext c) {
		File file = new File("E:\\tmp\\turbo.hprof");
		return new FileStreamResponse(file, 8192, false);
	}
}
