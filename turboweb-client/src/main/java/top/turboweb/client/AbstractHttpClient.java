package top.turboweb.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import org.apache.hc.core5.net.URIBuilder;
import top.turboweb.client.result.RestResponseResult;
import top.turboweb.commons.config.GlobalConfig;
import top.turboweb.commons.exception.TurboHttpClientException;
import top.turboweb.commons.utils.base.BeanUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * 抽象的Http客户端
 */
public abstract class AbstractHttpClient {

    protected final HttpClient httpClient;

    public AbstractHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 构建参数url
     * @param url 请求地址
     * @param params 参数
     * @return 返回一个promise
     */
    protected String buildParamUrl(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            params.forEach(uriBuilder::setParameter);
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new TurboHttpClientException("无效的url:" + url);
        }
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param forms 请求体
     * @return 返回一个promise
     */
    protected Mono<FullHttpResponse> doFormRequest(String url, HttpMethod method, HttpHeaders headers, Map<String, String> forms) {
        return httpClient
            .headers(h -> {
                if (headers != null) {
                    h.add(headers);
                }
            })
            .request(method)
            .uri(url)
            .sendForm((request, form) -> {
                if (forms != null && !forms.isEmpty()) {
                    forms.forEach(form::attr);
                }
            })
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }));
    }

    /**
     * 发起请求
     * @param url 请求地址
     * @param method 请求方法
     * @param headers 请求头
     * @param bodyContent 请求体
     * @return 返回一个promise
     */
    protected Mono<FullHttpResponse> doJsonRequest(String url, HttpMethod method, HttpHeaders headers, String bodyContent) {
        return httpClient
            .request(method)
            .uri(url)
            .send((request, outbound) -> {
                if (headers != null) {
                    request.headers(headers);
                }
                request.header(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
                if (bodyContent == null) {
                    return outbound;
                }
                return outbound.sendString(Mono.just(bodyContent));
            })
            .responseSingle((response, content) -> content.map(buf -> {
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                httpResponse.headers().add(response.responseHeaders());
                return httpResponse;
            }));
    }


    /**
     * 封装响应对象
     * @param response 响应
     * @param type 返回类型
     */
    protected  <T> RestResponseResult<T> packageResponse(FullHttpResponse response, Class<T> type) throws JsonProcessingException {
        ByteBuf contentBuf = response.content();
        // 判断是否是字符串
        if (type == String.class) {
            return new RestResponseResult<>(response.headers(), contentBuf != null ? (T) contentBuf.toString(GlobalConfig.getResponseCharset()) : (T) "");
        }
        String responseContent;
        if (contentBuf == null) {
            responseContent = "{}";
        } else {
            // 判断是否有数据
            if (contentBuf.readableBytes() == 0) {
                responseContent = "{}";
            } else {
                responseContent = contentBuf.toString(GlobalConfig.getResponseCharset());
            }
        }
        T value = BeanUtils.getObjectMapper().readValue(responseContent, type);
        return new RestResponseResult<>(response.headers(), value);
    }
}
