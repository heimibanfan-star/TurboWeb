package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Promise;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.utils.client.HttpClientUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TODO
 */
public class Client {
    public static void main(String[] args) throws InterruptedException {
        HttpClient httpClient = HttpClient.create();
        EventLoop loop = new NioEventLoopGroup().next();

        Promise<HttpResponse> promise = new DefaultPromise<>(loop);
        httpClient.get()
            .uri("http://localhost:8080/hello/sse")
            .response((response, content) -> {
                String contentType = response.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE);
                // 判断是否是sse
                if (Objects.equals(contentType, "text/event-stream")) {
                    // 将请求头写入channel
                    HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    httpResponse.headers().add(response.responseHeaders());
                    promise.setSuccess(httpResponse);
                    //                            System.out.println(buf.refCnt());
                    //                        buf.release();
                    // 发送 chunked 数据
                    return content.map(DefaultHttpContent::new);
                } else {
                    return content.map(buf -> {
                            buf.retain();
                            return buf;
                        })
                        .collectList().flatMap(bufList -> {
                            ByteBuf buf = bufList.getFirst();
                            FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                            httpResponse.headers().add(response.responseHeaders());
                            promise.setSuccess(httpResponse);
                            return Mono.empty();
                        });
                }
            })
            .subscribe(val -> {
                val.retain();
                loop.execute(() -> {
//                    try {
//                        Thread.sleep(2000);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
                    System.out.println(val.refCnt());
                    System.out.println(val.content().toString(Charset.defaultCharset()));
                });
            }, System.err::println);
        promise.addListener(future -> {
            if (future.isSuccess()) {
                Object futureNow = future.getNow();
                System.out.println(futureNow);
                if (futureNow instanceof FullHttpResponse fullHttpResponse) {
                    System.out.println(fullHttpResponse.content().toString(Charset.defaultCharset()));
                }
            }
        });
        CountDownLatch countDownLatch = new CountDownLatch(1);
        countDownLatch.await();
    }
}
