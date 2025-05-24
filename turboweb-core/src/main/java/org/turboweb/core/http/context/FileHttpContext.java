package org.turboweb.core.http.context;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;
import org.apache.commons.io.FileUtils;
import org.turboweb.core.connect.ConnectSession;
import org.turboweb.core.http.request.HttpInfoRequest;
import org.turboweb.core.http.response.HttpInfoResponse;
import org.turboweb.commons.exception.TurboFileException;
import org.turboweb.commons.exception.TurboResponseRepeatWriteException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 抽象的文件操作的上下文
 */
public abstract class FileHttpContext extends CoreHttpContext {

	private HttpContextFileHelper httpContextFileHelper;

	protected FileHttpContext(HttpInfoRequest request, HttpInfoResponse response, ConnectSession connectSession) {
		super(request, response, connectSession);
	}

	@Override
	public List<FileUpload> getFileUploads(String name) {
		return request.getContent().getFormFiles().get(name);
	}



	@Override
	public List<FileUpload> loadFiles(String fileName) {
		return request.getContent().getFormFiles().get(fileName);
	}

	@Override
	public FileUpload loadFile(String fileName) {
		List<FileUpload> fileUploads = request.getContent().getFormFiles().get(fileName);
		if (fileUploads == null || fileUploads.isEmpty()) {
			return null;
		} else {
			return fileUploads.getFirst();
		}
	}

	@Override
	public Void download(HttpResponseStatus status, byte[] bytes, String filename) {
		if (isWrite()) {
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
		setWrite();
		return end();
	}

	@Override
	public Void download(byte[] bytes, String filename) {
		return download(HttpResponseStatus.OK, bytes, filename);
	}

	@Override
	public Void download(HttpResponseStatus status, File file) {
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

	@Override
	public Void download(File file) {
		return download(HttpResponseStatus.OK, file);
	}

	@Override
	public Void download(HttpResponseStatus status, InputStream inputStream, String filename) {
		try {
			return download(status, inputStream.readAllBytes(), filename);
		} catch (IOException e) {
			throw new TurboFileException(e);
		}
	}

	@Override
	public Void download(InputStream inputStream, String filename) {
		return download(HttpResponseStatus.OK, inputStream, filename);
	}

	@Override
	public HttpContextFileHelper fileHelper() {
		if (httpContextFileHelper == null) {
			httpContextFileHelper = new HttpContextFileHelper(this);
		}
		return httpContextFileHelper;
	}
}
