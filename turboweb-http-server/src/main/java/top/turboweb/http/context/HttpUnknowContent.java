package top.turboweb.http.context;

import top.turboweb.http.core.Content;

/**
 * 未知内容：请求头未声明内容类型、或声明类型无匹配解析器时兜底承载请求体，
 * 不解析体、不暴露读取通道；体侧声明的类型由请求头 Content-Type 原文承载。
 */
public class HttpUnknowContent implements Content {
}
