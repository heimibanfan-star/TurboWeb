package top.turboweb.http.context;

import io.netty.handler.codec.http.HttpResponseStatus;
import top.turboweb.commons.anno.SyncOnce;

import java.io.File;
import java.io.InputStream;

/**
 * 响应的写入接口
 */
public interface ResponseWriter {
    /**
     * 结束响应
     *
     * @return null
     */
    @SyncOnce
    default Void end() {
        return null;
    }

    /**
     * 响应json数据
     *
     * @param status 响应状态
     * @param data   响应数据
     * return null
     */
    @SyncOnce
    Void json(HttpResponseStatus status, Object data);

    @SyncOnce
    Void json(Object data);

    @SyncOnce
    Void json(HttpResponseStatus status);

    /**
     * 响应文本数据
     *
     * @param status 响应状态
     * @param data   响应数据
     * @return null
     */
    @SyncOnce
    Void text(HttpResponseStatus status, String data);

    /**
     * 响应文本数据
     *
     * @param data 响应数据
     * @return null
     */
    @SyncOnce
    Void text(String data);

    /**
     * 响应html数据
     *
     * @param status 响应状态
     * @param data   响应数据
     * @return null
     */
    @SyncOnce
    Void html(HttpResponseStatus status, String data);

    /**
     * 响应html数据
     *
     * @param data 响应数据
     * @return null
     */
    @SyncOnce
    Void html(String data);

    boolean isWrite();

    /**
     * 设置写入
     */
    void setWrite();

    /**
     * 下载文件
     *
     * @param status   响应状态
     * @param bytes    文件的内容
     * @param filename 文件名
     * @return null
     */
    @SyncOnce
    Void download(HttpResponseStatus status, byte[] bytes, String filename);

    @SyncOnce
    Void download(byte[] bytes, String filename);

    @SyncOnce
    Void download(HttpResponseStatus status, File file);

    @SyncOnce
    Void download(File file);

    @SyncOnce
    Void download(HttpResponseStatus status, InputStream inputStream, String filename);

    @SyncOnce
    Void download(InputStream inputStream, String filename);

    /**
     * 文件下载扩展
     *
     * @return 文件下载扩展
     */
    HttpContextFileHelper fileHelper();
}
