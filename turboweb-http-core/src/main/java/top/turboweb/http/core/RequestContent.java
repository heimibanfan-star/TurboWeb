package top.turboweb.http.core;

/**
 * 请求体内容的读取契约：声明体侧内容类型，不携带关闭义务。
 * 无显式释放需求的实现只实现本接口即可；表单、JSON 等体读取能力以本接口的扩展承接。
 */
public interface RequestContent {

    /**
     * 获取体侧声明的内容类型。
     *
     * @return 内容类型；请求头未声明内容类型时返回 {@code null}
     */
    ContentType contentType();
}
