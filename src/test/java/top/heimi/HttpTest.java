package top.heimi;

import io.netty.channel.nio.NioEventLoopGroup;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.ReactiveHttpClient;
import org.turbo.web.utils.client.HttpClientUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO
 */
public class HttpTest {
    static AtomicInteger count = new AtomicInteger(0);
    public static void main(String[] args) throws InterruptedException {
        int num = 100000;

        HttpClientConfig httpClientConfig = new HttpClientConfig();
        httpClientConfig.setMaxConnections(1);
        HttpClientUtils.initClient(httpClientConfig, new NioEventLoopGroup(1));
        ReactiveHttpClient httpClient = HttpClientUtils.reactiveHttpClient();
        long start = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(num);
        httpClient.get("http://localhost:8080/hello/one", null, String.class)
                .subscribe((res) -> {
                    System.out.println("one" + res.getBody());
                });
        Thread.sleep(200);
        httpClient.get("http://localhost:8080/hello/two", null, String.class)
                .subscribe((res) -> {
                    System.out.println("two" + res.getBody());
                });
        latch.await();
    }
}
