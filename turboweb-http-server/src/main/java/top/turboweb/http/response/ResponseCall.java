package top.turboweb.http.response;

import top.turboweb.http.orchestrator.writer.ResponseWriter;

/**
 * 响应写出声明接口。
 * <p>
 * 实现该接口的响应对象声明由哪个 {@link ResponseWriter} 实现负责写出自身，
 * 响应写出管理器据此先查内置写出器表、未命中再查用户自定义登记表；
 * 未实现该接口、或声明类型两级均未命中的响应由默认写出器兜底处理。
 */
public interface ResponseCall {

    /**
     * 获取负责写出本响应的写出器类型。
     *
     * @return 写出器实现类的 Class 对象
     */
    Class<? extends ResponseWriter> getWriterType();
}
