package org.turbo.core.http.execetor;

import org.turbo.core.http.request.HttpInfoRequest;
import org.turbo.core.http.response.HttpInfoResponse;

/**
 * http分发器
 */
public interface HttpDispatcher {

    /**
     * 执行http请求的分发操作
     *
     * @param request 请求对象
     * @return 响应数据
     */
    HttpInfoResponse dispatch(HttpInfoRequest request);
}
