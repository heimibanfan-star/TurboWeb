package top.turboweb.core.listener;

/**
 * 服务器生命周期监听器
 */
public interface TurboWebListener {

    /**
     * 在服务器初始化之前调用
     */
    void beforeServerInit();

    /**
     * 在服务器启动之后调用
     */
    void afterServerStart();
}
