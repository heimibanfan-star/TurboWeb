package top.turboweb.http.context;

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.serializer.JsonSerializer;
import top.turboweb.http.connect.ConnectSession;
import top.turboweb.http.cookie.HttpCookieManager;
import top.turboweb.commons.exception.TurboArgsValidationException;
import top.turboweb.commons.exception.TurboParamParseException;
import top.turboweb.commons.utils.base.ValidationUtils;
import top.turboweb.http.session.HttpSession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code FullHttpContext} 是 TurboWeb 框架中最完整的 HTTP 上下文实现。
 * <p>
 * 它在 {@link FileHttpContext} 的基础上，进一步整合了：
 * <ul>
 *     <li>路径参数解析（Path Parameter）</li>
 *     <li>查询参数解析（Query Parameter）</li>
 *     <li>表单与 JSON 请求体反序列化</li>
 *     <li>参数校验（基于 JSR303）</li>
 * </ul>
 * <p>
 * 该类主要面向业务层使用者，提供统一的参数访问与数据绑定能力。
 * 它不关心路由分发或请求调度，仅负责当前请求作用域的数据封装与访问。
 *
 * @see FileHttpContext
 * @see HttpContext
 * @see top.turboweb.commons.utils.base.ValidationUtils
 */
public class FullHttpContext extends FileHttpContext implements HttpContext{

	private static final Logger log = LoggerFactory.getLogger(FullHttpContext.class);

	/**
	 * 路径参数映射（如 /user/{id} -> id）
	 */
	private Map<String, String> pathParams;

	/**
	 * 查询参数映射（来自 URL 中 ?key=value 的部分）
	 */
	private Map<String, List<String>> queryParams;

	/**
	 * 构造完整的 HTTP 上下文。
	 *
	 * @param request        HTTP 请求对象
	 * @param httpSession    会话对象
	 * @param cookieManager  Cookie 管理器
	 * @param connectSession 连接会话（用于 WebSocket / SSE）
	 * @param jsonSerializer JSON 序列化器
	 */
	public FullHttpContext(
			FullHttpRequest request,
			HttpSession httpSession,
			HttpCookieManager cookieManager,
			ConnectSession connectSession,
			JsonSerializer jsonSerializer
	) {
		super(request, httpSession, cookieManager, connectSession, jsonSerializer);
	}

	/**
	 * 校验对象参数合法性。
	 * <p>
	 * 若存在校验错误，将抛出 {@link TurboArgsValidationException}。
	 *
	 * @param obj 待校验对象
	 */
	@Override
	public void validate(Object obj) {
		List<String> errorMsg = ValidationUtils.validate(obj);
		if (!errorMsg.isEmpty()) {
			throw new TurboArgsValidationException(errorMsg);
		}
	}

	/**
	 * 校验对象参数合法性（带校验分组）。
	 *
	 * @param obj    待校验对象
	 * @param groups 校验分组
	 */
	@Override
	public void validate(Object obj, Class<?>... groups) {
		List<String> errorMsg = ValidationUtils.validate(obj, groups);
		if (!errorMsg.isEmpty()) {
			throw new TurboArgsValidationException(errorMsg);
		}
	}

	/**
	 * 注入路径参数（框架内部使用，开发者无需关心此方法）。
	 *
	 * @param params 路径参数键值映射
	 */
	@Override
	public void injectPathParam(Map<String, String> params) {
		this.pathParams = params;
	}

	/**
	 * 获取路径参数值。
	 *
	 * @param name 参数名
	 * @return 参数值，若不存在则返回 {@code null}
	 */
	@Override
	public String param(String name) {
		if (pathParams == null) {
			return null;
		}
		return pathParams.get(name);
	}

	/**
	 * 获取路径参数并转为 {@code int} 类型。
	 *
	 * @param name 参数名
	 * @return 整型值
	 * @throws TurboParamParseException 转换失败时抛出
	 */
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

	/**
	 * 获取路径参数并转为 {@code long} 类型。
	 */
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

	/**
	 * 获取路径参数并转为 {@code boolean} 类型。
	 */
	@Override
	public Boolean paramBool(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return Boolean.parseBoolean(param);
	}

	/**
	 * 获取路径参数并转为 {@code double} 类型。
	 */
	@Override
	public Double paramDouble(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return Double.parseDouble(param);
	}

	/**
	 * 获取路径参数并转为 {@link LocalDate}。
	 */
	@Override
	public LocalDate paramDate(String name) {
		String param = param(name);
		if (param == null) {
			return null;
		}
		return LocalDate.parse(param);
	}

	/**
	 * 解析 URL 中的查询参数。
	 *
	 * @param uri URI 字符串
	 * @return 参数名到参数值的映射
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

	/**
	 * 获取查询参数的所有值。
	 *
	 * @param name 参数名
	 * @return 值列表，若不存在则返回空列表
	 */
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

	/**
	 * 获取查询参数的第一个值。
	 */
	@Override
	public String query(String name) {
		List<String> vals = queries(name);
		return vals.isEmpty() ? null : vals.getFirst();
	}

	/**
	 * 获取查询参数的第一个值，若不存在返回默认值。
	 */
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

	/**
	 * 将查询参数转换为指定类型的对象。
	 */
	@Override
	public <T> T loadQuery(Class<T> beanType) {
		if (queryParams == null) {
			queryParams = parseQueryParams(request.uri());
		}
		// 处理map集合
		Map<String, Object> newMap = handleParamMap(queryParams);
		// 将集合转化为对象
		return jsonSerializer.mapToBean(newMap, beanType);
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

	/**
	 * 加载并反序列化表单参数。
	 */
	@Override
	public <T> T loadForm(Class<T> beanType) {
		Map<String, Object> newMap = handleParamMap(httpContent.getFormParams());
		return jsonSerializer.mapToBean(newMap, beanType);
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

	/**
	 * 反序列化 JSON 请求体为对象。
	 *
	 * @throws TurboParamParseException 当请求体为空时抛出
	 */
	@Override
	public <T> T loadJson(Class<T> beanType) {
		// 获取json请求体
		String jsonContent = httpContent.getJsonContent();
		if (jsonContent == null) {
			throw new TurboParamParseException("json请求体为空");
		}
		// 序列化对象
		return jsonSerializer.jsonToBean(jsonContent, beanType);
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
	 * 将 {@code Map<String, List<String>>} 转换为 {@code Map<String, Object>}。
	 * <p>
	 * 若参数值列表中仅有一个元素，则直接取第一个。
	 *
	 * @param oldMap 原始集合
	 * @return 转换后的集合
	 */
	private Map<String, Object> handleParamMap(Map<String, List<String>> oldMap) {
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
	 * 释放上下文资源。
	 * <p>
	 * 通常在请求结束后由框架自动调用。
	 */
	@Override
	public void release() {
		httpContent.release();
	}
}
