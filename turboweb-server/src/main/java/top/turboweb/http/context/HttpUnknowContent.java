package top.turboweb.http.context;

import top.turboweb.rest.ContentType;
import top.turboweb.rest.Http;

public class HttpUnknowContent implements Http.Content {

    private final ContentType contentType;

    public HttpUnknowContent(ContentType contentType) {
        this.contentType = contentType;
    }

    @Override
    public ContentType contentType() {
        return this.contentType;
    }
}
