package org.turbo.web.core.config;

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
     * session最大不活跃时间
     */
    private long sessionMaxNotUseTime = -1;

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
