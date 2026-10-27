package top.turboweb.http.context;

import top.turboweb.rest.core.ContentType;
import top.turboweb.rest.core.HttpContent;

public class HttpUnknowContent implements HttpContent {

    private final ContentType contentType;

    public HttpUnknowContent(ContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public ContentType contentType() {
        return this.contentType;
    }
}
