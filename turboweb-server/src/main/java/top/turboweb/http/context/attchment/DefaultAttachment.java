package top.turboweb.http.context.attchment;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认的上下文挂载对象
 */
public class DefaultAttachment implements Attachment {

    private final Map<String, Object> attachments = new HashMap<>();

    @Override
    public void attach(Object obj) {
        // 获取对象的全限定名
        String key = obj.getClass().getName();
        // 存储对象
        attachments.put(key, obj);
    }

    @Override
    public void attach(String key, Object obj) {
        attachments.put(key, obj);
    }

    @Override
    public Object getAttachment(String key) {
        return attachments.get(key);
    }

    @Override
    public <T> T getAttachment(String key, Class<T> clazz) {
        Object obj = attachments.get(key);
        if (obj == null) {
            return null;
        }
        return clazz.cast(obj);
    }

    @Override
    public <T> T getAttachment(Class<T> clazz) {
        String key = clazz.getName();
        return getAttachment(key, clazz);
    }
}
