package top.turboweb.http.core;

/**
 * 请求体内容的标记契约：实现类以具体类暴露体读取能力，读取侧认其具体类转型取数。
 * 无显式释放需求的实现只实现本接口即可；表单、JSON 等体读取能力以本接口的扩展承接。
 */
public interface Content {
}
