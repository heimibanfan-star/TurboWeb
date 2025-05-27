package org.example.clientexample;

import io.netty.channel.nio.NioEventLoopGroup;
import top.turboweb.client.HttpClientUtils;
import top.turboweb.client.PromiseHttpClient;
import top.turboweb.client.config.HttpClientConfig;
import top.turboweb.client.result.RestResponseResult;

import java.util.concurrent.ExecutionException;

public class ClientExample {
	public static void main(String[] args) throws ExecutionException, InterruptedException {
		HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
		PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
		RestResponseResult<String> result = promiseHttpClient.get("http://127.0.0.1:8080/hello", null, String.class).get();
		String resultBody = result.getBody();
		System.out.println(resultBody);
	}
}
