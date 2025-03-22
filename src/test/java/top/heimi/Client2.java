package top.heimi;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * TODO
 */
public class Client2 {
    public static void main(String[] args) throws InterruptedException {
        ByteBuf buf1 = Unpooled.copiedBuffer("hello", Charset.defaultCharset());
        ByteBuf buf2 = Unpooled.copiedBuffer("world", Charset.defaultCharset());

        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();
        compositeByteBuf.addComponents(buf1, buf2);
        System.out.println(compositeByteBuf.toString(Charset.defaultCharset()));
    }
}
