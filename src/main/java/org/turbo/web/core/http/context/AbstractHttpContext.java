package org.turbo.web.core.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.core.http.cookie.HttpCookie;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.session.Session;
import org.turbo.web.core.http.sse.SseResponse;
import org.turbo.web.core.http.sse.SseSession;
import org.turbo.web.core.http.sse.SseResultObject;
import org.turbo.web.exception.TurboArgsValidationException;
import org.turbo.web.exception.TurboParamParseException;
import org.turbo.web.exception.TurboSerializableException;
import org.turbo.web.exception.TurboSseException;
import org.turbo.web.utils.common.BeanUtils;
import org.turbo.web.utils.common.ValidationUtils;
import org.turbo.web.utils.http.HttpResponseUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽象的Http上下文
 */
public abstract class AbstractHttpContext {
    protected static final Logger log = LoggerFactory.getLogger(HttpContext.class);
    protected final HttpInfoRequest request;
    protected final HttpInfoResponse response;
    private final Map<String, String> pathVariables = new HashMap<>();
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();
    protected final SseSession sseSession;
    protected final boolean sseOpen = false;

    public AbstractHttpContext(HttpInfoRequest request, HttpInfoResponse response, SseSession session) {
        this.request = request;
        this.response = response;
        this.sseSession = session;
    }

    /**
     * 是否开启sse会话
     *
     * @return 是否开启sse会话
     */
    public boolean isSseOpen() {
        return sseOpen;
    }

    public HttpInfoRequest getRequest() {
        return request;
    }

    public HttpInfoResponse getResponse() {
        return response;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    /**
     * 打开sse会话
     *
     * @return sse会话
     */
    public SseResultObject openSseSession() {
        if (sseSession == null) {
            throw new TurboSseException("sseSession为空");
        } else {
            HttpResponse sseResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpResponseUtils.mergeHeaders(response, sseResponse);
            sseResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
            sseResponse.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
            sseResponse.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            sseResponse.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
            HttpUtil.setTransferEncodingChunked(sseResponse, true); // 开启 Chunked 传输
            // 封装对象
            return new SseResultObject(sseResponse, sseSession);
        }
    }

    public SseResponse newSseResponse() {
        return new SseResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, response.headers(), sseSession);
    }

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadQueryParamToBean(Class<T> beanType) {
        try {
            // 获取无参构造方法
            Constructor<T> constructor = beanType.getConstructor();
            // 创建实例对象
            T instance = constructor.newInstance();
            // 处理map集合
            Map<String, Object> newMap = handleOldMap(request.getQueryParams());
            // 将集合转化为对象
            BeanUtils.mapToBean(newMap, instance);
            return instance;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            log.error("封装查询参数失败", e);
            throw new TurboParamParseException(e.getMessage());
        }
    }

    /**
     * 将查询参数封装为对象并进行数据校验
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadValidQueryParamToBean(Class<T> beanType) {
        T bean = this.loadQueryParamToBean(beanType);
        validate(bean);
        return bean;
    }

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 对象类型
     * @return T 对象
     */
    public <T> T loadFormParamToBean(Class<T> beanType) {
        // 获取无参构造方法
        try {
            Constructor<T> constructor = beanType.getConstructor();
            T instance = constructor.newInstance();
            Map<String, Object> newMap = handleOldMap(request.getContent().getFormParams());
            BeanUtils.mapToBean(newMap, instance);
            return instance;
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
            log.error("封装表单参数失败", e);
            throw new TurboParamParseException(e.getMessage());
        }
    }

    /**
     * 将表单参数封装成对象并进行数据校验
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadValidJsonParamToBean(Class<T> beanType) {
        T bean = this.loadJsonParamToBean(beanType);
        validate(bean);
        return bean;
    }

    /**
     * 将json参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadJsonParamToBean(Class<T> beanType) {
        // 获取json请求体
        String jsonContent = request.getContent().getJsonContent();
        if (jsonContent == null) {
            throw new TurboParamParseException("json请求体为空");
        }
        // 序列化对象
        try {
            return objectMapper.readValue(jsonContent, beanType);
        } catch (JsonProcessingException e) {
            log.error("序列化失败", e);
            throw new TurboSerializableException(e.getMessage());
        }
    }

    /**
     * 将表单参数封装成对象并进行数据校验
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadValidFormParamToBean(Class<T> beanType) {
        T bean = this.loadFormParamToBean(beanType);
        validate(bean);
        return bean;
    }

    /**
     * 获取文件上传的信息
     *
     * @param name 属性名
     * @return 文件上传的列表
     */
    public List<FileUpload> getFileUploads(String name) {
        return request.getContent().getFormFiles().get(name);
    }

    /**
     * 处理旧的map，将单个内容提取出来
     *
     * @param oldMap 旧集合
     * @return 新集合
     */
    public Map<String, Object> handleOldMap(Map<String, List<String>> oldMap) {
        Map<String, Object> newMap = new HashMap<>();
        oldMap.forEach((key, value) -> {
            if (value.size() == 1) {
                newMap.put(key, value.getFirst());
            } else {
                newMap.put(key, value);
            }
        });
        return newMap;
    }

    /**
     * 校验对象
     *
     * @param obj 对象
     */
    public void validate(Object obj) {
        List<String> errorMsg = ValidationUtils.validate(obj);
        if (!errorMsg.isEmpty()) {
            throw new TurboArgsValidationException(errorMsg);
        }
    }

    /**
     * 获取cookie
     *
     * @return cookie
     */
    public HttpCookie getHttpCookie() {
        return new HttpCookie(request.getCookies(), response);
    }

    /**
     * 获取session
     *
     * @return session
     */
    public Session getSession() {
        return request.getSession();
    }
}
