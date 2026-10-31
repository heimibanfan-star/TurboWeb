package top.turboweb.http.request;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.core.HttpRequestMessage;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.core.RequestLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 表单请求消息：请求体以 {@link FormData} 承载，
 * 内容对象由 {@code FormContentParser} 解码产出，本类只做持有，各访问器重复访问返回同一值。
 *
 * <p>请求方式与请求目标由请求行承接；头部读写由 {@link HttpRequestMessage} 的基类承载。</p>
 */
public class FormRequestMessage extends HttpRequestMessage<FormRequestMessage.FormData> {

    /**
     * 建立表单请求消息。
     *
     * @param requestLine 请求行，不能为空
     * @param content     表单请求体内容，不能为空
     * @throws NullPointerException 当 {@code requestLine} 或 {@code content} 为 {@code null} 时抛出
     */
    public FormRequestMessage(RequestLine requestLine, FormData content) {
        super(requestLine, content);
    }

    /**
     * 表单请求体内容：持有普通表单（{@code application/x-www-form-urlencoded}）
     * 解码产出的字段归档。字段取值均为堆上字符串，本类不持有任何需归还资源，
     * 不实现 {@link AutoCloseable}、不背负关闭义务。
     *
     * <p>多部分表单（{@code multipart/form-data}）由子类 {@link MultipartFormData} 承载。</p>
     */
    public static class FormData {

        /** 普通字段归档：key 为参数名、value 为值列表，一次成型后不再变化 */
        private final Map<String, List<String>> formParams;

        /**
         * 持有解码产出的字段归档。
         *
         * @param formParams 普通字段归档，不能为空
         * @throws NullPointerException 当 {@code formParams} 为 {@code null} 时抛出
         */
        public FormData(Map<String, List<String>> formParams) {
            this.formParams = Objects.requireNonNull(formParams, "formParams can not be null");
        }

        /**
         * 获取普通字段归档。
         *
         * @return key 为参数名、value 为值列表的映射
         */
        public Map<String, List<String>> formParams() {
            return this.formParams;
        }
    }

    /**
     * 多部分表单请求体内容：在普通字段归档之上持有上传文件归档，
     * 上传文件段经 {@link FormFile} 包装后以 {@link MultipartFile} 暴露，不直接对外透出 Netty 类型。
     *
     * <p><b>内存安全契约：</b>上传文件段的数据体由构造传入的表单解码器持有
     * （可能占用直接内存或磁盘临时文件），本类私有持有该解码器、仅作释放锚：
     * 必须在请求处理结束后经 {@link #close()} 销毁解码器并回收全部上传数据；
     * close 经释放闩保证幂等，二次调用无副作用。close 之后 {@link #formFiles()}
     * 中的 {@link MultipartFile} 即失效，对其继续访问的后果由使用者自行承担，框架不做守护。</p>
     */
    public static class MultipartFormData extends FormData implements AutoCloseable {

        /** 上传文件归档：key 为文件字段名、value 为该字段文件列表，一次成型后不再变化 */
        private final Map<String, List<MultipartFile>> formFiles;

        /** 表单解码器：解析完成后由本类持有，仅作数据释放锚，终身不替换，close 经释放闩保证仅销毁一次 */
        private final HttpPostRequestDecoder decoder;

        /** close 幂等闩：已销毁解码器后置位 */
        private boolean released;

        /**
         * 持有解码产出的字段与文件归档及数据释放锚。
         *
         * @param formParams 普通字段归档，不能为空
         * @param formFiles  上传文件归档，不能为空
         * @param decoder    表单解码器，上传数据体归其持有，不能为空
         * @throws NullPointerException 当任一参数为 {@code null} 时抛出
         */
        public MultipartFormData(Map<String, List<String>> formParams,
                                 Map<String, List<MultipartFile>> formFiles,
                                 HttpPostRequestDecoder decoder) {
            super(formParams);
            this.formFiles = Objects.requireNonNull(formFiles, "formFiles can not be null");
            this.decoder = Objects.requireNonNull(decoder, "decoder can not be null");
        }

        /**
         * 获取上传文件归档。
         *
         * @return key 为文件字段名、value 为该字段 {@link MultipartFile} 列表的映射
         */
        public Map<String, List<MultipartFile>> formFiles() {
            return this.formFiles;
        }

        /**
         * 销毁解码器并回收其归档的全部请求体数据（含上传文件的直接内存与磁盘临时文件）。
         * 幂等：释放后置位闩，重复调用不再触发解码器销毁。
         */
        @Override
        public void close() {
            if (!released) {
                decoder.destroy();
                released = true;
            }
        }
    }

    /**
     * 上传文件内容的服务端实现：内部聚合 Netty 解码器产出的 {@link FileUpload}，
     * 文件名、内容类型、内容长度与内容读取均委托给被聚合的上传段，不直接对外透出 Netty 类型。
     *
     * <p>上传段可能驻留堆外内存或磁盘临时文件，其生命周期由持有解码器的 {@link MultipartFormData}
     * 统一管理：本类不复制内容（{@link #readAll()} 除外，它按契约返回独立字节数组）、不承担释放义务，
     * 也不实现 {@link AutoCloseable}。请求处理结束后继续使用本类读取数据，与直接访问已释放的上传段
     * 风险相同；需要跨请求保留内容时，应在处理结束前经 {@link #readAll()} 或 {@link #inputStream()}
     * 完成复制。</p>
     *
     * <p>{@link #inputStream()} 每次调用都自内容起始处新建互不干扰的读取通道：内存驻留的上传段包装
     * 底层缓冲区读取，磁盘驻留的上传段打开其临时文件读取，均不把整段内容载入内存。</p>
     */
    public static class FormFile implements MultipartFile {

        /** 被聚合的 Netty 上传段，数据体生命周期归其解码器所有 */
        private final FileUpload fileUpload;

        /** 构造期解析并持有的内容类型，解析结果不再变化 */
        private final ContentType contentType;

        /**
         * 包装 Netty 上传段。
         *
         * @param fileUpload 被聚合的上传段，不能为空
         * @throws NullPointerException 当 {@code fileUpload} 为 {@code null} 时抛出
         */
        public FormFile(FileUpload fileUpload) {
            this.fileUpload = Objects.requireNonNull(fileUpload, "fileUpload can not be null");
            this.contentType = parseContentType(fileUpload.getContentType());
        }

        /**
         * 打开内容输入流，自内容起始处开始读取。
         *
         * <p>每次调用新建读取通道，不与先前打开的流共享读取位置；关闭返回的流不释放上传段内容。</p>
         *
         * @return 输入流，永不为 {@code null}
         * @throws IOException 上传段驻留磁盘且打开临时文件失败时抛出
         */
        @Override
        public InputStream inputStream() throws IOException {
            if (!fileUpload.isInMemory()) {
                return Files.newInputStream(fileUpload.getFile().toPath());
            }
            ByteBuf content = fileUpload.getByteBuf();
            return content == null ? InputStream.nullInputStream() : new ByteBufInputStream(content.duplicate());
        }

        /**
         * 读取全部内容：直接取上传段的整体字节，不做流包装。
         *
         * @return 全部内容，永不为 {@code null}；空文件返回长度为 0 的数组
         * @throws IOException 读取上传段失败时抛出
         */
        @Override
        public byte[] readAll() throws IOException {
            return fileUpload.get();
        }

        /**
         * 获取浏览器提交的原始文件名。
         *
         * @return 原始文件名；来源未提供文件名时返回 {@code null}
         */
        @Override
        public String filename() {
            return fileUpload.getFilename();
        }

        /**
         * 获取上传段声明的内容类型。
         *
         * <p>MIME 类型之外的 charset 参数在构造期一并解析：无法识别的 charset 视同未声明，
         * 只保留 MIME 类型。</p>
         *
         * @return 内容类型；未声明或解析不出 MIME 类型时返回 {@code null}
         */
        @Override
        public ContentType contentType() {
            return contentType;
        }

        /**
         * 获取上传段的内容字节数。
         *
         * @return 内容长度，单位字节，非负
         */
        @Override
        public long length() {
            return fileUpload.length();
        }

        /**
         * 解析上传段声明的 Content-Type 取值。
         *
         * @param value 原始 Content-Type 取值，可为 {@code null}
         * @return 解析出的内容类型；取值缺失、MIME 类型为空时返回 {@code null}，
         *         charset 无法识别时只保留 MIME 类型
         */
        private static ContentType parseContentType(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split(";");
            String mimeType = parts[0].trim();
            if (mimeType.isEmpty()) {
                return null;
            }
            Charset charset = null;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    String charsetName = stripQuotes(part.substring("charset=".length()).trim());
                    if (charsetName.isEmpty()) {
                        continue;
                    }
                    try {
                        charset = Charset.forName(charsetName);
                    } catch (IllegalArgumentException ignore) {
                        // 无法识别的字符集视同未声明
                    }
                }
            }
            return ContentType.create(mimeType, charset);
        }

        /**
         * 去掉参数值两侧成对的双引号（HTTP 参数允许以 quoted-string 形式取值）。
         */
        private static String stripQuotes(String value) {
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1).trim();
            }
            return value;
        }
    }
}
