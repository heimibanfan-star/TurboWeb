package top.turboweb.client;

/**
 * 流式请求的取消句柄：可在任意回调内或任意线程发起取消，
 * 取消后引擎终止响应接收，未递送的分块不再到达，流以终态回调收场。
 */
public interface StreamHandle {

    /**
     * 取消本流：重复调用只生效一次，取消动作本身不抛出异常。
     */
    void cancel();
}
