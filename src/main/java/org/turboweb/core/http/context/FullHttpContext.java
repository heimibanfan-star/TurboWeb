package org.turboweb.core.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.turboweb.core.connect.ConnectSession;
import org.turboweb.core.http.request.HttpInfoRequest;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.core.http.response.sync.InternalSseEmitter;
import org.turboweb.core.http.response.sync.SseEmitter;
import org.turboweb.exception.TurboArgsValidationException;
import org.turboweb.exception.TurboParamParseException;
import org.turboweb.exception.TurboResponseRepeatWriteException;
import org.turboweb.exception.TurboSerializableException;
import org.turboweb.utils.common.BeanUtils;
import org.turboweb.utils.common.ValidationUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完成的HttpContext实现类
 */
public class FullHttpContext extends FileHttpContext implements HttpContext{

	private static final Logger log = LoggerFactory.getLogger(FullHttpContext.class);
	private Map<String, String> pathParams;

	public FullHttpContext(HttpInfoRequest request, HttpInfoResponse response, ConnectSession connectSession) {
		super(request, response, connectSession);
	}

	@Override
	public void validate(Object obj) {
		List<String> errorMsg = ValidationUtils.validate(obj);
		if (!errorMsg.isEmpty()) {
			throw new TurboArgsValidationException(errorMsg);
		}
	}

	@Override
	public Void json(HttpResponseStatus status, Object data) {
		if (isWrite()) {
			throw new TurboResponseRepeatWriteException("response repeat write");
		}
		response.setStatus(status);
		try {
			response.setContent(BeanUtils.getObjectMapper().writeValueAsString(data));
		} catch (JsonProcessingException e) {
			throw new TurboSerializableException(e.getMessage());
		}
		response.setContentType("application/json;charset=utf-8");
		setWrite();
		return end();
	}

	@Override
	public Void json(Object data) {
		return json(HttpResponseStatus.OK, data);
	}

	@Override
	public Void json(HttpResponseStatus status) {
		return json(status, "");
	}

	@Override
	public Void text(HttpResponseStatus status, String data) {
		if (isWrite()) {
			throw new TurboResponseRepeatWriteException("response repeat write");
		}
		response.setStatus(status);
		response.setContent(data);
		response.setContentType("text/plain;charset=utf-8");
		setWrite();
		return end();
	}

	@Override
	public Void text(String data) {
		return text(HttpResponseStatus.OK, data);
	}

	@Override
	public Void html(HttpResponseStatus status, String data) {
		if (isWrite()) {
			throw new TurboResponseRepeatWriteException("response repeat write");
		}
		response.setStatus(status);
		response.setContent(data);
		response.setContentType("text/html;charset=utf-8");
		setWrite();
		return end();
	}

	@Override
	public Void html(String data) {
		return html(HttpResponseStatus.OK, data);
	}

	@Override
	public void injectPathParam(Map<String, String> params) {
		this.pathParams = params;
	}

	@Override
	public String param(String name) {
		if (pathParams == null) {
			return null;
		}
		return pathParams.get(name);
	}

	@Override
	public Integer paramInt(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		try {
			return Integer.parseInt(param);
		} catch (NumberFormatException e) {
			throw new TurboParamParseException(e);
		}
	}

	@Override
	public Long paramLong(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		try {
			return Long.parseLong(param);
		} catch (NumberFormatException e) {
			throw new TurboParamParseException(e);
		}
	}

	@Override
	public Boolean paramBoolean(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return Boolean.parseBoolean(param);
	}

	@Override
	public <T> T loadQuery(Class<T> beanType) {
		try {
			// 获取无参构造方法
			Constructor<T> constructor = beanType.getConstructor();
			// 创建实例对象
			T instance = constructor.newInstance();
			// 处理map集合
			Map<String, Object> newMap = handleParamMap(request.getQueryParams());
			// 将集合转化为对象
			BeanUtils.mapToBean(newMap, instance);
			return instance;
		} catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
			log.error("封装查询参数失败", e);
			throw new TurboParamParseException(e.getMessage());
		}
	}

	@Override
	public <T> T loadValidQuery(Class<T> beanType) {
		T result = this.loadQuery(beanType);
		validate(result);
		return result;
	}

	@Override
	public <T> T loadForm(Class<T> beanType) {
		// 获取无参构造方法
		try {
			Constructor<T> constructor = beanType.getConstructor();
			T instance = constructor.newInstance();
			Map<String, Object> newMap = handleParamMap(request.getContent().getFormParams());
			BeanUtils.mapToBean(newMap, instance);
			return instance;
		} catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {
			log.error("封装表单参数失败", e);
			throw new TurboParamParseException(e.getMessage());
		}
	}

	@Override
	public <T> T loadValidForm(Class<T> beanType) {
		T result = this.loadForm(beanType);
		validate(result);
		return result;
	}

	@Override
	public <T> T loadJson(Class<T> beanType) {
		// 获取json请求体
		String jsonContent = request.getContent().getJsonContent();
		if (jsonContent == null) {
			throw new TurboParamParseException("json请求体为空");
		}
		// 序列化对象
		try {
			return BeanUtils.getObjectMapper().readValue(jsonContent, beanType);
		} catch (JsonProcessingException e) {
			log.error("序列化失败", e);
			throw new TurboSerializableException(e.getMessage());
		}
	}

	@Override
	public <T> T loadValidJson(Class<T> beanType) {
		T result = this.loadJson(beanType);
		validate(result);
		return result;
	}

	@Override
	public SseEmitter newSseEmitter() {
		return new InternalSseEmitter(connectSession, 32);
	}

	@Override
	public SseEmitter newSseEmitter(int maxMessageCache) {
		return new InternalSseEmitter(connectSession, maxMessageCache);
	}

	/**
	 * 处理旧的map，将单个内容提取出来
	 *
	 * @param oldMap 旧集合
	 * @return 新集合
	 */
	public Map<String, Object> handleParamMap(Map<String, List<String>> oldMap) {
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
}
