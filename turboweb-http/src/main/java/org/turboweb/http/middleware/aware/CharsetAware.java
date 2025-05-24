package org.turboweb.http.middleware.aware;

import java.nio.charset.Charset;

/**
 * 注入框架编码的aware
 */
public interface CharsetAware {

    /**
     * 设置编码
     * @param charset 编码
     */
    void setCharset(Charset charset);
}
