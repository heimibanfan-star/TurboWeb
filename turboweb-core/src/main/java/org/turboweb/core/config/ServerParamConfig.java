package org.turboweb.core.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 服务器初始化配置类
 */
public class ServerParamConfig {

    /**
     * 最大请求内容长度
     */
    private int maxContentLength = 1024 * 1024 * 10;

    /**
     * 字符编码
     */
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * 是否显示请求日志
     */
    private boolean showRequestLog = true;

    /**
     * session检查时间间隔
     */
    private long sessionCheckTime = 300000;

    /**
     * reactive线程池大小
     */
    private int reactiveServiceThreadNum = 8;

    public int getReactiveServiceThreadNum() {
        return reactiveServiceThreadNum;
    }

    public void setReactiveServiceThreadNum(int reactiveServiceThreadNum) {
        this.reactiveServiceThreadNum = reactiveServiceThreadNum;
    }

    /**
     * session最大不活跃时间
     */
    private long sessionMaxNotUseTime = -1;

    private long checkForSessionNum = 256;

    public long getCheckForSessionNum() {
        return checkForSessionNum;
    }

    public void setCheckForSessionNum(long checkForSessionNum) {
        this.checkForSessionNum = checkForSessionNum;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public boolean isShowRequestLog() {
        return showRequestLog;
    }

    public void setShowRequestLog(boolean showRequestLog) {
        this.showRequestLog = showRequestLog;
    }

    public long getSessionCheckTime() {
        return sessionCheckTime;
    }

    public void setSessionCheckTime(long sessionCheckTime) {
        this.sessionCheckTime = sessionCheckTime;
    }

    public long getSessionMaxNotUseTime() {
        return sessionMaxNotUseTime;
    }

    public void setSessionMaxNotUseTime(long sessionMaxNotUseTime) {
        this.sessionMaxNotUseTime = sessionMaxNotUseTime;
    }
}
