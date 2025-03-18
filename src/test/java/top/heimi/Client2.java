package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;

/**
 * TODO
 */
public class Client2 {
    public static void main(String[] args) throws InterruptedException {
        HttpClient httpClient = HttpClient.create();
        String json = "{\"name\":\"tom\",\"age\":18}";
        ByteBuf byteBuf = Unpooled.wrappedBuffer(json.getBytes());
        httpClient
            .request(HttpMethod.GET)
            .uri("http://localhost:8080/hello/sse")
            .response((response, content) -> content)
            .map(buf -> {
                return new DefaultHttpContent(buf);
            })
            .subscribe(buf -> {
                System.out.println(buf.refCnt());
            });
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }
}
