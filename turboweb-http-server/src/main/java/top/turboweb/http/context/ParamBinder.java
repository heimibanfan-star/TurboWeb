package top.turboweb.http.context;

import io.netty.handler.codec.http.multipart.FileUpload;

import java.time.LocalDate;
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
     * 校验对象
     *
     * @param obj 待校验对象
     * @param groups 待校验分组
     */
    void validate(Object obj, Class<?>... groups);

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

    Boolean paramBool(String name);

    Double paramDouble(String name);

    LocalDate paramDate(String name);

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

    List<Double> queriesDouble(String name);

    Double queryDouble(String name);

    Double queryDouble(String name, double defaultValue);

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadQuery(Class<T> beanType);

    <T> T loadValidQuery(Class<T> beanType);

    /**
     * 将查询参数封装为对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidQuery(Class<T> beanType, Class<?>... groups);

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadForm(Class<T> beanType);

    <T> T loadValidForm(Class<T> beanType);

    /**
     * 将表单参数封装成对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidForm(Class<T> beanType, Class<?>... groups);

    /**
     * 将json参数封装成对象
     *
     * @param beanType 对象类型
     * @return 对象
     */
    <T> T loadJson(Class<T> beanType);

    <T> T loadValidJson(Class<T> beanType);

    /**
     * 将json参数封装成对象
     *
     * @param beanType 待封装对象类型
     * @param groups 待校验分组
     * @return 封装对象
     */
    <T> T loadValidJson(Class<T> beanType, Class<?>... groups);

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
