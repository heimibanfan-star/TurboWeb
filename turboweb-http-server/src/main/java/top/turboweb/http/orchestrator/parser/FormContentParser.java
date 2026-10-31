package top.turboweb.http.orchestrator.parser;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import top.turboweb.http.core.ContentType;
import top.turboweb.http.core.MultipartFile;
import top.turboweb.http.exception.RequestParseException;
import top.turboweb.http.request.FormRequestMessage;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 表单内容解析器：同时承接 {@code application/x-www-form-urlencoded} 与
 * {@code multipart/form-data} 请求体，解析产出 {@link FormRequestMessage.FormData}；
 * 多部分请求体产出其子类 {@link FormRequestMessage.MultipartFormData}，
 * 解码器随内容对象移交、由其承担释放锚职责。
 */
public class FormContentParser implements HttpContentParser {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(FormContentParser.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<ContentType> contentTypes() {
        return Set.of(ContentType.APPLICATION_FORM_URLENCODED, ContentType.MULTIPART_FORM_DATA);
    }

    /**
     * 解析表单请求体：解码在解析期一次完成，普通字段与上传文件段当场归档，
     * 产出的内容对象不再携带请求对象引用，构造返回后调用方可立即释放请求。
     *
     * @param request        表单请求对象，不能为空；Content-Type 非表单类型或字段解码失败时拒绝
     * @param defaultCharset 请求头未声明 charset 时的缺省解码编码，不能为空
     * @return urlencoded 请求产出 {@link FormRequestMessage.FormData}，
     *         multipart 请求产出 {@link FormRequestMessage.MultipartFormData}
     * @throws NullPointerException  当 {@code request} 或 {@code defaultCharset} 为 {@code null} 时抛出
     * @throws RequestParseException 当 Content-Type 不是 {@code application/x-www-form-urlencoded}
     *                               或 {@code multipart/form-data}，或字段解码失败时抛出
     */
    @Override
    public Object parse(FullHttpRequest request, Charset defaultCharset) {
        Objects.requireNonNull(request, "request can not be null");
        Objects.requireNonNull(defaultCharset, "defaultCharset can not be null");
        String mimeType = HttpRequestHelper.getContentType(request);
        boolean multipart = HttpHeaderValues.MULTIPART_FORM_DATA.contentEqualsIgnoreCase(mimeType);
        if (!multipart && !HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.contentEqualsIgnoreCase(mimeType)) {
            throw new RequestParseException("contentType is not application/x-www-form-urlencoded or multipart/form-data");
        }
        // 判定一次的解码字符集同时传给数据工厂与解码器：工厂缺省为 UTF-8，
        // 若不显式传入，属性取值会把非 UTF-8 请求体按 UTF-8 解出乱码
        Charset charset = HttpRequestHelper.getRequestCharset(request, defaultCharset);
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(Integer.MAX_VALUE, charset);
        HttpPostRequestDecoder decoder;
        try {
            // Netty 4.2 的解码器在构造期即完成请求体解析，非法请求体的异常也发生在这一行
            decoder = new HttpPostRequestDecoder(factory, request, charset);
        } catch (RuntimeException | Error ex) {
            // 解码器未建成：无法接管其生命周期，交工厂清掉已登记的残留数据
            factory.cleanRequestHttpDatas(request);
            throw ex;
        }
        Map<String, List<String>> params = new HashMap<>();
        Map<String, List<MultipartFile>> files = new HashMap<>();
        try {
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
                    files.computeIfAbsent(name, k -> new ArrayList<>(1)).add(new FormRequestMessage.FormFile(fileUpload));
                }
            }
        } catch (RuntimeException | Error ex) {
            // 归档中途失败：数据体已归解码器持有，销毁后再传播，不留悬挂资源
            decoder.destroy();
            throw ex;
        }
        if (multipart) {
            // 上传数据体（直接内存或磁盘临时文件）归解码器持有，随内容对象移交，由其 close 释放
            return new FormRequestMessage.MultipartFormData(params, files, decoder);
        }
        // 普通表单字段取值均为堆上字符串，解码器当场销毁，产出对象无关闭义务
        decoder.destroy();
        return new FormRequestMessage.FormData(params);
    }
}
