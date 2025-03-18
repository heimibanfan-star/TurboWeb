package top.heimi;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.PromiseHttpClient;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Demo {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
        PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
        RestResponseResult<String> responseResult = promiseHttpClient.get(
            "http://localhost:8080/hello",
            HttpMethod.GET,
            null,
            null,
            String.class
        ).get();
        System.out.println(responseResult.getBody());
    }
}
