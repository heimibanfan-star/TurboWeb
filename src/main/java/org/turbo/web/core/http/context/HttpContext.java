package org.turbo.web.core.http.context;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.turbo.web.anno.End;
import org.turbo.web.core.connect.ConnectSession;
import org.turbo.web.core.http.cookie.HttpCookie;
import org.turbo.web.core.http.request.HttpInfoRequest;
import org.turbo.web.core.http.response.HttpInfoResponse;
import org.turbo.web.core.http.response.SseResponse;
import org.turbo.web.core.http.session.Session;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * http上下文
 */
public interface HttpContext {

	HttpInfoRequest getRequest();

	HttpInfoResponse getResponse();

	ConnectSession getConnectSession();

	SseResponse newSseResponse();

	/**
	 * 获取文件上传的信息
	 *
	 * @param name 属性名
	 * @return 文件上传的列表
	 */
	List<FileUpload> getFileUploads(String name);

	/**
	 * 校验对象
	 *
	 * @param obj 对象
	 */
	void validate(Object obj);

	/**
	 * 获取cookie
	 *
	 * @return cookie
	 */
	HttpCookie getHttpCookie();

	/**
	 * 获取session
	 *
	 * @return session
	 */
	Session getSession();

	/**
	 * 结束响应
	 *
	 * @return 结果
	 */
	@End
	default Object end() {
		return null;
	}

	/**
	 * 响应json数据
	 *
	 * @param status 响应状态
	 * @param data   响应数据
	 */
	@End
	Object json(HttpResponseStatus status, Object data);

	@End
	Object json(Object data);

	@End
	Object json(HttpResponseStatus status);

	@End
	Object text(HttpResponseStatus status, String data);

	@End
	Object text(String data);

	@End
	Object html(HttpResponseStatus status, String data);

	@End
	Object html(String data);

	boolean isWrite();

	/**
	 * 设置写入
	 */
	void setWrite();

	/**
	 * 注入路径参数
	 *
	 * @param params 路径参数
	 */
	void injectPathParam(Map<String, String> params);

	/**
	 * 获取路径参数
	 *
	 * @param name 参数名
	 * @return 参数值
	 */
	String param(String name);

	/**
	 * 将查询参数封装为对象
	 *
	 * @param beanType 对象类型
	 * @return 对象
	 */
	<T> T loadQuery(Class<T> beanType);

	<T> T loadValidQuery(Class<T> beanType);

	/**
	 * 将表单参数封装成对象
	 *
	 * @param beanType 对象类型
	 * @return 对象
	 */
	<T> T loadForm(Class<T> beanType);

	<T> T loadValidForm(Class<T> beanType);

	/**
	 * 将json参数封装成对象
	 *
	 * @param beanType 对象类型
	 * @return 对象
	 */
	<T> T loadJson(Class<T> beanType);

	<T> T loadValidJson(Class<T> beanType);

	/**
	 * 获取文件上传对象
	 *
	 * @param fileName 文件名
	 * @return 文件上传对象
	 */
	List<FileUpload> loadFiles(String fileName);

	/**
	 * 获取文件上传对象
	 *
	 * @param fileName 文件名
	 * @return 文件上传对象
	 */
	FileUpload loadFile(String fileName);

	/**
	 * 下载文件
	 *
	 * @param status   响应状态
	 * @param bytes      文件的内容
	 * @param filename 文件名
	 * @return 结果
	 */
	@End
	Object download(HttpResponseStatus status, byte[] bytes, String filename);

	@End
	Object download(byte[] bytes, String filename);

	@End
	Object download(HttpResponseStatus status, File file);

	@End
	Object download(File file);

	@End
	Object download(HttpResponseStatus status, InputStream inputStream, String filename);

	@End
	Object download(InputStream inputStream, String filename);

	/**
	 * 文件下载扩展
	 *
	 * @return 文件下载扩展
	 */
	HttpContextFileHelper fileHelper();
}
