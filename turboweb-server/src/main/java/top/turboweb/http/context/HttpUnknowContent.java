package top.turboweb.http.context;

import top.turboweb.http.core.constant.ContentType;

public class HttpUnknowContent implements HttpContext.Content {

    private final ContentType contentType;

    public HttpUnknowContent(ContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public ContentType contentType() {
        return this.contentType;
    }
}
