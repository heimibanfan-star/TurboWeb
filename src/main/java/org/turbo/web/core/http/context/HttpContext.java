package org.turbo.web.core.http.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.apache.commons.io.FileUtils;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * http请求的上下文
 */
public class HttpContext extends CoreHttpContext {

	private Middleware chain;
	private final ObjectMapper objectMapper = BeanUtils.getObjectMapper();
	private HttpContextFileHelper httpContextFileHelper;

	/**
	 * 是否已经写入内容
	 */
	protected boolean isWrite = false;

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

	/**
	 * 获取文件上传对象
	 *
	 * @param fileName 文件名
	 * @return 文件上传对象
	 */
	public List<FileUpload> loadFiles(String fileName) {
		return request.getContent().getFormFiles().get(fileName);
	}

	/**
	 * 获取文件上传对象
	 *
	 * @param fileName 文件名
	 * @return 文件上传对象
	 */
	public FileUpload loadFile(String fileName) {
		List<FileUpload> fileUploads = request.getContent().getFormFiles().get(fileName);
		if (fileUploads == null || fileUploads.isEmpty()) {
			return null;
		} else {
			return fileUploads.getFirst();
		}
	}

	/**
	 * 下载文件
	 *
	 * @param status   响应状态
	 * @param bytes      文件的内容
	 * @param filename 文件名
	 * @return 结果
	 */
	@End
	public Object download(HttpResponseStatus status, byte[] bytes, String filename) {
		if (isWrite) {
			throw new TurboResponseRepeatWriteException("response repeat write");
		}
		response.setContent(bytes);
		response.setContentType("application/octet-stream");
		String filenameHeader = "";
		if (filename != null && !filename.isEmpty()) {
			filename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
			filenameHeader = "filename=\"" + filename + "\"";
		} else {
			filename = UUID.randomUUID().toString();
			filenameHeader = "filename=\"" + filename + "\"";
		}
		response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment;" + filenameHeader);
		response.setStatus(status);
		isWrite = true;
		return end();
	}

	@End
	public Object download(byte[] bytes, String filename) {
		return download(HttpResponseStatus.OK, bytes, filename);
	}

	@End
	public Object download(HttpResponseStatus status, File file) {
		if (!file.exists()) {
			throw new TurboFileException("file not exists," + file.getAbsolutePath());
		}
		if (file.isDirectory()) {
			throw new TurboFileException("file is directory," + file.getAbsolutePath());
		}
		if (!file.canRead()) {
			throw new TurboFileException("file can not read," + file.getAbsolutePath());
		}
		try {
			byte[] bytes = FileUtils.readFileToByteArray(file);
			return download(status, bytes, file.getName());
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
	}

	@End
	public Object download(File file) {
		return download(HttpResponseStatus.OK, file);
	}

	@End
	public Object download(HttpResponseStatus status, InputStream inputStream, String filename) {
		try {
			return download(status, inputStream.readAllBytes(), filename);
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
	}

	@End
	public Object download(InputStream inputStream, String filename) {
		return download(HttpResponseStatus.OK, inputStream, filename);
	}

	/**
	 * 文件下载扩展
	 *
	 * @return 文件下载扩展
	 */
	public HttpContextFileHelper fileHelper() {
		if (httpContextFileHelper == null) {
			httpContextFileHelper = new HttpContextFileHelper(this);
		}
		return httpContextFileHelper;
	}

}
