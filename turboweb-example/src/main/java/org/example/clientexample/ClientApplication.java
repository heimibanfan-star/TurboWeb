package org.example.clientexample;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import top.turboweb.client.HttpClientUtils;
import top.turboweb.client.PromiseHttpClient;
import top.turboweb.client.result.RestResponseResult;
import top.turboweb.core.server.BootStrapTurboWebServer;
import top.turboweb.core.server.TurboWebServer;

import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ClientApplication {
	public static void main(String[] args) throws ExecutionException, InterruptedException {
		TurboWebServer server = new BootStrapTurboWebServer(ClientApplication.class);
		server.http().controller(new UserController());
		server.start(8080);

//		example01();
//		example02();
//		example03();
//		example04();
		example05();
	}

	public static void example01() throws ExecutionException, InterruptedException {
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		HttpHeaders headers = new DefaultHttpHeaders();
		RestResponseResult<String> result = promiseHttpClient.get(
			"http://127.0.0.1:8080/user/example01",
			headers,
			Map.of("name", "TurboWeb", "age", "18"),
			String.class
		).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}

	public static void example02() throws ExecutionException, InterruptedException {
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		RestResponseResult<Map> result = promiseHttpClient.get(
			"http://127.0.0.1:8080/user/example02",
			Map.of("name", "TurboWeb", "age", "18")
		).get();
		Map resultBody = result.getBody();
		System.out.println(resultBody);
	}

	public static void example03() throws ExecutionException, InterruptedException {
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		HttpHeaders headers = new DefaultHttpHeaders();
		headers.set("Authorization", "heimibanfan");
		RestResponseResult<String> result = promiseHttpClient.postForm(
			"http://127.0.0.1:8080/user/example03",
			headers,
			Map.of("name", "TurboWeb", "age", "18"),
			null,
			String.class
		).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}

	public static void example04() throws ExecutionException, InterruptedException {
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		RestResponseResult<String> result = promiseHttpClient.postJson(
			"http://127.0.0.1:8080/user/example04",
			null,
			Map.of("name", "TurboWeb", "age", "18"),
			String.class
		).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}

	public static void example05() throws ExecutionException, InterruptedException {
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		RestResponseResult<String> result = promiseHttpClient.postJson(
			"http://127.0.0.1:8080/user/example05",
			Map.of("name", "heimibanfan"),
			Map.of("name", "TurboWeb", "age", "18"),
			String.class
		).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}
}
