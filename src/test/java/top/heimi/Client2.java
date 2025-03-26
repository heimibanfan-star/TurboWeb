package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.PromiseHttpClient;
import org.turbo.web.core.http.client.ReactiveHttpClient;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * TODO
 */
public class Client2 {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
        PromiseHttpClient promiseHttpClient = HttpClientUtils.promiseHttpClient();
        promiseHttpClient.get("http://localhost:8080/hello", new HashMap<>())
            .addListener(future -> {
                if (future.isSuccess()) {
                    RestResponseResult<String> responseResult = (RestResponseResult<String>) future.get();
                    System.out.println(responseResult.getHeaders());
                    System.out.println(responseResult.getBody());
                } else {
                    future.cause().printStackTrace();
                }
            });
    }
}

class UserDTO {

}