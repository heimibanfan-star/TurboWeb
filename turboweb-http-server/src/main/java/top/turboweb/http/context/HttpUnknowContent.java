package top.turboweb.http.context;

import top.turboweb.http.core.ContentType;
import top.turboweb.http.core.RequestContent;

public class HttpUnknowContent implements RequestContent {

    private final ContentType contentType;

    public HttpUnknowContent(ContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public ContentType contentType() {
        return this.contentType;
    }
}
