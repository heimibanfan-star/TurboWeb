package top.turboweb.rest.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MultipartFile} 的 default 方法：readAll 经输入流读取全部内容并关闭流，
 * isEmpty 按内容长度判定。
 */
class MultipartFileTest {

    /** 记录关闭状态的字节流：readAll 未关闭流即断言失配 */
    private static final class TrackingStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** 仅数据通道可用的桩实现：元信息方法给最小行为，default 方法不触碰它们 */
    private static MultipartFile stub(byte[] content) {
        TrackingStream stream = new TrackingStream(content);
        return new MultipartFile() {
            @Override
            public InputStream inputStream() {
                return stream;
            }

            @Override
            public String filename() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ContentType contentType() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long length() {
                return content.length;
            }
        };
    }

    @Test
    void readAllReturnsWholeContentAndClosesStream() throws IOException {
        byte[] content = {'a', 'b', 'c'};
        MultipartFile file = stub(content);

        assertArrayEquals(content, file.readAll());
        assertTrue(((TrackingStream) file.inputStream()).closed, "readAll 应关闭自己打开的输入流");
    }

    @Test
    void readAllReturnsEmptyArrayForEmptyContent() throws IOException {
        assertArrayEquals(new byte[0], stub(new byte[0]).readAll());
    }

    @Test
    void isEmptyFollowsLength() {
        assertTrue(stub(new byte[0]).isEmpty());
        assertFalse(stub(new byte[]{'a'}).isEmpty());
    }
}
