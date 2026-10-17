package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.rest.ContentType;
import top.turboweb.http.core.helper.HttpRequestHelper;
import top.turboweb.http.exception.RequestParseException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code HttpFormContent} 持有表单类请求体（{@code application/x-www-form-urlencoded} 与
 * {@code multipart/form-data}）的解析结果。
 *
 * <p>解码器在构造期即完成请求体解码；普通字段（取值为堆上字符串）与上传文件段
 * （{@link FileUpload}，数据体由 Netty 解码器管理，可能占用直接内存或磁盘临时文件）
 * 的分类归档不即时进行，改由首个访问器调用时遍历解码数据建立，一次建立后缓存复用。
 * 本类不持有请求对象的引用，因此<b>不负责释放传入的 {@link FullHttpRequest}</b>：
 * 请求体字节在构造期已转入解码器持有的数据对象，构造返回后调用方可立即释放请求。
 *
 * <p><b>内存安全契约：</b>本类所持有的解码器及其归档数据必须在请求处理结束后经
 * {@link #close()} 释放；close 销毁解码器并回收全部上传数据，解码器为构造期建立的
 * final 持有、经释放闩保证幂等，二次调用无副作用。close 之后 {@link #formFiles()}
 * 中的 {@link FileUpload} 即失效，对其继续访问的后果由使用者自行承担，框架不做守护。
 *
 * <p>解码字符集按请求头 Content-Type 声明优先、未声明时回落到构造传入的缺省字符集，
 * 与请求体其余解码口径一致。
 */
public class HttpFormContent implements AutoCloseContent {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(HttpFormContent.class);

    /** 构造期判定的表单内容类型，urlencoded 与 multipart 二选一 */
    private final ContentType contentType;

    /** 普通字段缓存：首个访问器调用时建立，与 {@link #formFiles} 同一次遍历成型 */
    private Map<String, List<String>> formParams;

    /** 上传文件缓存：首个访问器调用时建立，数据体生命周期随解码器 */
    private Map<String, List<FileUpload>> formFiles;

    /** 表单解码器：构造期解析完成后持有，终身不替换，close 经释放闩保证仅销毁一次 */
    private final HttpPostRequestDecoder decoder;

    /** close 幂等闩：已销毁解码器后置位 */
    private boolean released;

    /**
     * 从请求即时解析表单内容。
     *
     * @param request        表单请求对象，不能为空；Content-Type 非表单类型时本构造拒绝
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码，不能为空
     * @throws NullPointerException  当 {@code request} 或 {@code defaultCharset} 为 {@code null} 时抛出
     * @throws RequestParseException 当 Content-Type 不是 {@code application/x-www-form-urlencoded}
     *                               或 {@code multipart/form-data}，或字段解码失败时抛出
     */
    public HttpFormContent(FullHttpRequest request, Charset defaultCharset) {
        Objects.requireNonNull(request, "request can not be null");
        Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
        String mimeType = HttpRequestHelper.getContentType(request);
        boolean multipart = HttpHeaderValues.MULTIPART_FORM_DATA.contentEqualsIgnoreCase(mimeType);
        if (!multipart && !HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.contentEqualsIgnoreCase(mimeType)) {
            throw new RequestParseException("contentType is not application/x-www-form-urlencoded or multipart/form-data");
        }
        this.contentType = multipart ? ContentType.MULTIPART_FORM_DATA : ContentType.APPLICATION_FORM_URLENCODED;
        // 判定一次的解码字符集同时传给数据工厂与解码器：工厂缺省为 UTF-8，
        // 若不显式传入，属性取值会把非 UTF-8 请求体按 UTF-8 解出乱码
        Charset charset = HttpRequestHelper.getRequestCharset(request, defaultCharset);
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(Integer.MAX_VALUE, charset);
        HttpPostRequestDecoder decoder;
        try {
            // Netty 4.2 的解码器在构造期即完成请求体解析，非法请求体的异常也发生在这一行；
            // 构造只建解码器，字段与文件的归档遍历推迟到访问器首访
            decoder = new HttpPostRequestDecoder(factory, request, charset);
        } catch (RuntimeException | Error ex) {
            // 解码器未建成：构造无法接管其生命周期，交工厂清掉已登记的残留数据
            factory.cleanRequestHttpDatas(request);
            throw ex;
        }
        this.decoder = decoder;
    }

    /**
     * 懒建字段与文件归档缓存：未建立时遍历解码器的全量请求体数据，
     * 普通字段取值进 {@link #formParams}，上传文件段进 {@link #formFiles}，
     * 两个映射在遍历全部成功后一并赋值；已建立则直接返回。
     * 本类按请求单线程使用，缓存建立不加同步。
     */
    private void ensureParsed() {
        if (formParams != null) {
            return;
        }
        Map<String, List<String>> params = new HashMap<>();
        Map<String, List<FileUpload>> files = new HashMap<>();
        for (InterfaceHttpData httpData : decoder.getBodyHttpDatas()) {
            if (httpData instanceof Attribute attribute) {
                String name = attribute.getName();
                String value;
                try {
                    value = attribute.getValue();
                    if (name == null || name.isBlank()) {
                        log.error("Empty name found in form attribute, key: {}, value: {}", name, value);
                        continue;
                    }
                } catch (IOException e) {
                    log.error("Failed to parse form data", e);
                    throw new RequestParseException("Failed to parse request body");
                }
                params.computeIfAbsent(name, k -> new ArrayList<>(1)).add(value);
            } else if (httpData instanceof FileUpload fileUpload) {
                String name = fileUpload.getName();
                if (name == null || name.isBlank()) {
                    log.error("Empty name found in file upload, name: {}", name);
                }
                files.computeIfAbsent(name, k -> new ArrayList<>(1)).add(fileUpload);
            }
        }
        this.formParams = params;
        this.formFiles = files;
    }

    /**
     * 获取上传文件集合，缓存未建立时先完成归档遍历。
     *
     * @return key 为文件字段名、value 为该字段文件列表的映射；urlencoded 请求为空 Map
     */
    public Map<String, List<FileUpload>> formFiles() {
        ensureParsed();
        return formFiles;
    }

    /**
     * 获取表单普通字段集合，缓存未建立时先完成归档遍历。
     *
     * @return key 为参数名、value 为值列表的映射
     */
    public Map<String, List<String>> formParams() {
        ensureParsed();
        return formParams;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContentType contentType() {
        return contentType;
    }

    /**
     * 销毁解码器并回收其归档的全部请求体数据（含上传文件的直接内存与磁盘临时文件）。
     * 幂等：释放后置位闩，重复调用不再触发解码器销毁。
     */
    @Override
    public void close() throws Exception {
        if (!released) {
            decoder.destroy();
            released = true;
        }
    }
}
