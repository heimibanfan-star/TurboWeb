package top.heimi.controller;

import io.netty.handler.codec.http.multipart.FileUpload;
import org.turbo.web.anno.*;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.response.FileRegionResponse;
import org.turbo.web.core.http.response.ViewModel;
import top.heimi.pojos.User;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * TODO
 */
@RequestPath("/user")
public class UserController {

	@Get("/1")
	public void get1(HttpContext ctx) {
		User user = ctx.loadQuery(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Get("/2")
	public void get2(HttpContext ctx) {
		User user = ctx.loadValidQuery(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Get("/3/{id}/{sex}")
	public void get3(HttpContext ctx) {
		User user = ctx.loadQuery(User.class);
		System.out.println(ctx.param("id"));
		System.out.println(ctx.param("sex"));
		System.out.println(user);
		ctx.text("hello world");
	}

	@Post("/1")
	public void post1(HttpContext ctx) {
		User user = ctx.loadForm(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Post("/2")
	public void post2(HttpContext ctx) {
		User user = ctx.loadValidForm(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Post("/3")
	public void post3(HttpContext ctx) {
		User user = ctx.loadJson(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Post("/4")
	public void post4(HttpContext ctx) {
		User user = ctx.loadValidJson(User.class);
		System.out.println(user);
		ctx.text("hello world");
	}

	@Get("/upload")
	public void upload(HttpContext ctx) {
		FileUpload fileUpload = ctx.loadFile("file");
		System.out.println(fileUpload);
		ctx.text("upload");
	}

	@Post("/upload")
	public void upload2(HttpContext ctx) throws IOException {
		FileUpload fileUpload = ctx.loadFile("file");
		System.out.println(fileUpload);
		File file = new File("E:\\tmp\\" + fileUpload.getFilename());
		boolean newFile = file.createNewFile();
		if (newFile) {
			fileUpload.renameTo(file);
		}
		ctx.text("upload");
	}

	@Get("/zerocopy")
	public FileRegionResponse zeroCopy(HttpContext ctx) {
		String path = "C:\\Users\\heimi\\Downloads\\goland-2024.3.exe";
		return new FileRegionResponse(new File(path));
	}

	@Get("/download1")
	public void download1(HttpContext ctx) {
		String path = "C:\\Users\\heimi\\Downloads\\goland-2024.3.exe";
		ctx.download(new File(path));
	}

	@Get("/file1")
	public void file1(HttpContext ctx) throws FileNotFoundException {
		String path = "E:\\javaCodeDev\\turbo-web\\src\\test\\resources\\static\\img.png";
		ctx.fileHelper().png(new FileInputStream(path));
	}

	@Get
	public ViewModel getView(HttpContext ctx) {
		ViewModel viewModel = new ViewModel();
		viewModel.setViewName("index");
		viewModel.addAttribute("name", "turbo");
		return viewModel;
	}

}
