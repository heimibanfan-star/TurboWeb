package org.turbo.web.core.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turbo.web.anno.End;
import org.turbo.web.core.http.middleware.Middleware;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.connect.ConnectSession;
import org.turbo.web.exception.*;
import org.turbo.web.utils.common.BeanUtils;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * http请求的上下文
 */
public class HttpContext extends AbstractHttpContext{

    private static final Logger log = LoggerFactory.getLogger(HttpContext.class);
    private Middleware chain;
    private final Map<String, String> pathVariables = new HashMap<>();
    private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();

    /**
     * 是否已经写入内容
     */
    private boolean isWrite = false;

    public HttpContext(HttpInfoRequest request, HttpInfoResponse response, Middleware chain, ConnectSession session) {
        super(request, response, session);
        this.chain = chain;
    }

    /**
     * 执行下一个中间件
     *
     * @return 执行结果
     */
    public Object doNext() {
        Middleware current = chain;
        if (current == null) {
            return null;
        }
        chain = chain.getNext();
        return current.invoke(this);
    }

    /**
     * 执行流式响应
     *
     * @return 响应对象
     */
    public Mono<?> doNextMono() {
        Object object = doNext();
        if (object instanceof Mono<?> mono) {
            return mono;
        } else {
            throw new TurboReactiveException("反应式结果类型应该是Mono");
        }
    }

    /**
     * 结束响应
     *
     * @return 结果
     */
    @End
    public Object end() {
        return null;
    }

    /**
     * 响应json数据
     *
     * @param status 响应状态
     * @param data   响应数据
     */
    @End
    public Object json(HttpResponseStatus status, Object data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        try {
            response.setContent(objectMapper.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new TurboSerializableException(e.getMessage());
        }
        response.setContentType("application/json;charset=utf-8");
        isWrite = true;
        chain = null;
        return end();
    }

    @End
    public Object json(Object data) {
        return json(HttpResponseStatus.OK, data);
    }

    @End
    public Object json(HttpResponseStatus status) {
        return json(status, "");
    }

    @End
    public Object text(HttpResponseStatus status, String data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        response.setContent(data);
        response.setContentType("text/plain;charset=utf-8");
        isWrite = true;
        chain = null;
        return end();
    }

    @End
    public Object text(String data) {
        return text(HttpResponseStatus.OK, data);
    }

    @End
    public Object html(HttpResponseStatus status, String data) {
        if (isWrite) {
            throw new TurboResponseRepeatWriteException("response repeat write");
        }
        response.setStatus(status);
        response.setContent(data);
        response.setContentType("text/html;charset=utf-8");
        isWrite = true;
        chain = null;
        return end();
    }

    @End
    public Object html(String data) {
        return html(HttpResponseStatus.OK, data);
    }

    public boolean isWrite() {
        return isWrite;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public String getPathVariable(String name) {
        return pathVariables.get(name);
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
     * 获取路径参数
     *
     * @param name 参数名
     * @return 参数值
     */
    public String param(String name) {
        return getPathVariable(name);
    }

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadQuery(Class<T> beanType) {
        return loadQueryParamToBean(beanType);
    }

    public <T> T loadValidQuery(Class<T> beanType) {
        return loadValidQueryParamToBean(beanType);
    }

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadForm(Class<T> beanType) {
        return loadFormParamToBean(beanType);
    }

    public <T> T loadValidForm(Class<T> beanType) {
        return loadValidFormParamToBean(beanType);
    }

    /**
     * 将json参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    public <T> T loadJson(Class<T> beanType) {
        return loadJsonParamToBean(beanType);
    }

    public <T> T loadValidJson(Class<T> beanType) {
        return loadValidJsonParamToBean(beanType);
    }
}
