package top.heimi;

import io.netty.channel.nio.NioEventLoopGroup;
import org.turbo.web.core.config.HttpClientConfig;
import org.turbo.web.core.http.client.ReactiveHttpClient;
import org.turbo.web.utils.client.HttpClientUtils;

import java.util.concurrent.CountDownLatch;

/**
 * TODO
 */
public class HttpTest {
    public static void main(String[] args) throws InterruptedException {
        int num = 1000000;
        HttpClientConfig httpClientConfig = new HttpClientConfig();
        HttpClientUtils.initClient(httpClientConfig, new NioEventLoopGroup(8));
        ReactiveHttpClient httpClient = HttpClientUtils.reactiveHttpClient();
        long start = System.currentTimeMillis();

        CountDownLatch latch = new CountDownLatch(num);
        for (int i = 0; i < num; i++) {
            httpClient.get("http://localhost:8080/hello", null, String.class)
                    .doFinally(signalType -> latch.countDown())
                    .subscribe();
        }
        latch.await();
        System.out.println("耗时：" + (System.currentTimeMillis() - start) + "ms");
    }
}
