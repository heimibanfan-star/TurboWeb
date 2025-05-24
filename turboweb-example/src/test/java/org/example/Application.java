package org.example;

import io.netty.channel.nio.NioEventLoopGroup;
import org.example.controller.HelloController;
import org.turboweb.client.HttpClientUtils;
import org.turboweb.client.PromiseHttpClient;
import org.turboweb.client.config.HttpClientConfig;
import org.turboweb.client.result.RestResponseResult;
import org.turboweb.core.server.StandardTurboWebServer;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Application {
	public static void main(String[] args) throws ExecutionException, InterruptedException {
		HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
		PromiseHttpClient client = HttpClientUtils.promiseHttpClient();
		RestResponseResult<String> result = client.get("http://www.baidu.com", null, String.class).get();
		System.out.println(result.getBody());
	}
}
