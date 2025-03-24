package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.ReactiveHttpClient;
import org.turbo.web.core.http.client.result.RestResponseResult;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * TODO
 */
public class Client2 {
    public static void main(String[] args) throws InterruptedException {
        HttpClientUtils.initClient(new HttpClientConfig(), new NioEventLoopGroup());
        ReactiveHttpClient reactiveHttpClient = HttpClientUtils.reactiveHttpClient();
        reactiveHttpClient.get("http://localhost:8080/order", null)
            .map(RestResponseResult::getBody)
            .subscribe(System.out::println);
    }
}
