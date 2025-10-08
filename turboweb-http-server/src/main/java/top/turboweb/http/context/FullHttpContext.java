package top.turboweb.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.commons.exception.TurboArgsValidationException;
import top.turboweb.commons.exception.TurboParamParseException;
import top.turboweb.commons.exception.TurboSerializableException;
import top.turboweb.commons.utils.base.BeanUtils;
import top.turboweb.commons.utils.base.ValidationUtils;
import top.turboweb.http.session.HttpSession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 完成的HttpContext实现类
 */
public class FullHttpContext extends FileHttpContext implements HttpContext{

	private static final Logger log = LoggerFactory.getLogger(FullHttpContext.class);
	private Map<String, String> pathParams;
	private Map<String, List<String>> queryParams;

	public FullHttpContext(FullHttpRequest request, HttpSession httpSession, HttpCookieManager cookieManager, ConnectSession connectSession) {
		super(request, httpSession, cookieManager, connectSession);
	}

	@Override
	public void validate(Object obj) {
		List<String> errorMsg = ValidationUtils.validate(obj);
		if (!errorMsg.isEmpty()) {
			throw new TurboArgsValidationException(errorMsg);
		}
	}

	@Override
	public void validate(Object obj, Class<?>... groups) {
		List<String> errorMsg = ValidationUtils.validate(obj, groups);
		if (!errorMsg.isEmpty()) {
			throw new TurboArgsValidationException(errorMsg);
		}
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
	public Boolean paramBool(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return Boolean.parseBoolean(param);
	}

	@Override
	public Double paramDouble(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return Double.parseDouble(param);
	}

	@Override
	public LocalDate paramDate(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return LocalDate.parse(param);
	}

	/**
	 * 解析url中的参数
	 *
	 * @param uri uri地址
	 * @return 参数map
	 */
	private static Map<String, List<String>> parseQueryParams(String uri) {
		Map<String, List<String>> paramsForSearch = new HashMap<>();
		try {
			URIBuilder uriBuilder = new URIBuilder(uri);
			// 获取所有的查询参数
			List<NameValuePair> params = uriBuilder.getQueryParams();
			for (NameValuePair param : params) {
				paramsForSearch
						.computeIfAbsent(param.getName(), k -> new ArrayList<>(1))
						.add(param.getValue());
			}
		} catch (Exception e) {
			log.error("解析url参数失败", e);
		}
		return paramsForSearch;
	}

	@Override
	public List<String> queries(String name) {
		if (queryParams == null) {
			queryParams = parseQueryParams(request.uri());
		}
		List<String> vals = queryParams.get(name);
		if (vals == null) {
			return List.of();
		}
		return vals;
	}

	@Override
	public String query(String name) {
		List<String> vals = queries(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	@Override
	public String query(String name, String defaultValue) {
		String val = query(name);
		return val == null ? defaultValue : val;
	}

	@Override
	public List<Long> queriesLong(String name) {
		return queries(name).stream().map(Long::parseLong).toList();
	}

	@Override
	public Long queryLong(String name) {
		List<Long> vals = queriesLong(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	@Override
	public Long queryLong(String name, long defaultValue) {
		Long val = queryLong(name);
		return val == null ? defaultValue : val;
	}

	@Override
	public List<Integer> queriesInt(String name) {
		return queries(name).stream().map(Integer::parseInt).toList();
	}

	@Override
	public Integer queryInt(String name) {
		List<Integer> vals = queriesInt(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	@Override
	public Integer queryInt(String name, int defaultValue) {
		Integer val = queryInt(name);
		return val == null ? defaultValue : val;
	}

	@Override
	public List<Boolean> queriesBool(String name) {
		return queries(name).stream().map(Boolean::parseBoolean).toList();
	}

	@Override
	public Boolean queryBool(String name) {
		List<Boolean> vals = queriesBool(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	@Override
	public Boolean queryBool(String name, Boolean defaultValue) {
		Boolean val = queryBool(name);
		return val == null ? defaultValue : val;
	}

	@Override
	public List<Double> queriesDouble(String name) {
		return queries(name).stream().map(Double::parseDouble).toList();
	}

	@Override
	public Double queryDouble(String name) {
		List<Double> vals = queriesDouble(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	@Override
	public Double queryDouble(String name, double defaultValue) {
		Double val = queryDouble(name);
		return val == null ? defaultValue : val;
	}

	@Override
	public <T> T loadQuery(Class<T> beanType) {
		if (queryParams == null) {
			queryParams = parseQueryParams(request.uri());
		}
		// 处理map集合
		Map<String, Object> newMap = handleParamMap(queryParams);
		// 将集合转化为对象
		return BeanUtils.mapToBean(newMap, beanType);
	}

	@Override
	public <T> T loadValidQuery(Class<T> beanType) {
		T result = this.loadQuery(beanType);
		validate(result);
		return result;
	}

	@Override
	public <T> T loadValidQuery(Class<T> beanType, Class<?>... groups) {
		T result = this.loadQuery(beanType);
		validate(result, groups);
		return result;
	}

	@Override
	public <T> T loadForm(Class<T> beanType) {
		Map<String, Object> newMap = handleParamMap(httpContent.getFormParams());
		return BeanUtils.mapToBean(newMap, beanType);
	}

	@Override
	public <T> T loadValidForm(Class<T> beanType) {
		T result = this.loadForm(beanType);
		validate(result);
		return result;
	}

	@Override
	public <T> T loadValidForm(Class<T> beanType, Class<?>... groups) {
		T result = this.loadForm(beanType);
		validate(result, groups);
		return result;
	}

	@Override
	public <T> T loadJson(Class<T> beanType) {
		// 获取json请求体
		String jsonContent = httpContent.getJsonContent();
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
	public <T> T loadValidJson(Class<T> beanType, Class<?>... groups) {
		T result = this.loadJson(beanType);
		validate(result, groups);
		return result;
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

	/**
	 * 释放资源
	 */
	@Override
	public void release() {
		httpContent.release();
	}
}
