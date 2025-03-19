package top.heimi;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.concurrent.Promise;
import org.apache.hc.core5.net.URIBuilder;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.PromiseHttpClient;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Demo {
    public static void main(String[] args) throws ExecutionException, InterruptedException, URISyntaxException {
        HttpClientConfig config = new HttpClientConfig();
        HttpClientUtils.initClient(config, new NioEventLoopGroup());
        PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
        Promise<RestResponseResult<String>> resultPromise = promiseHttpClient.postJson("http://localhost:8080/hello", new HashMap<>(), null, String.class);
        System.out.println(resultPromise.get().getBody());
    }
}
