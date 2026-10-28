package top.turboweb.server.runtime;

/**
 * 资源查找异常：按类型获取资源命中多个资源，或按名字获取到的资源与期望类型不符时抛出。
 */
public class ResourceException extends RuntimeException {

    public ResourceException(String message) {
        super(message);
    }
}
