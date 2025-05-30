package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;

import java.util.List;
import java.util.Map;

/**
 * 参数绑定的接口
 */
public interface ParamBinder {

    /**
     * 校验对象
     *
     * @param obj 对象
     */
    void validate(Object obj);

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

    Integer paramInt(String name);

    Long paramLong(String name);

    Boolean paramBoolean(String name);

    /**
     * 获取查询参数
     *
     * @param name 参数名
     * @return 参数值
     */
    List<String> queries(String name);

    String query(String name);

    String query(String name, String defaultValue);

    List<Long> queriesLong(String name);

    Long queryLong(String name);

    Long queryLong(String name, long defaultValue);

    List<Integer> queriesInt(String name);

    Integer queryInt(String name);

    Integer queryInt(String name, int defaultValue);

    List<Boolean> queriesBool(String name);

    Boolean queryBool(String name);

    Boolean queryBool(String name, Boolean defaultValue);

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
}
