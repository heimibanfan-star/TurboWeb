package top.turboweb.common.utils.resource;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 作用域中绑定的资源。
 * <p>
 * 一个作用域对应一段生命周期（例如一个应用从装配到关闭）：装配期创建的可关闭资源用
 * {@link #register(AutoCloseable)} 登记进来，生命周期结束时由 {@link #close()} 按登记顺序
 * 统一释放。单个资源释放失败只记录日志，不影响其余资源的释放。
 */
public class ScopedResource {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(ScopedResource.class);

    /**
     * 已登记的资源；用LinkedHashSet保持登记顺序，使释放顺序与登记顺序一致、可预期
     */
    private final Set<AutoCloseable> registedResource = new LinkedHashSet<>();
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * 作用域是否已释放：保证每个登记资源至多被关闭一次，并且释放后不再接受新的登记
     */
    private boolean released = false;

    /**
     * 登记资源到本作用域，重复登记同一资源立即抛出 {@link IllegalStateException}
     *
     * @param resource 可关闭资源，不能为null
     * @throws IllegalStateException 资源重复登记，或作用域已经释放
     * @throws NullPointerException  资源为null
     */
    public void register(AutoCloseable resource) {
        Objects.requireNonNull(resource, "登记的资源不能为null");
        lock.lock();
        try {
            if (released) {
                throw new IllegalStateException("资源作用域已释放，不能再登记资源");
            }
            if (registedResource.contains(resource)) {
                throw new IllegalStateException("资源重复注册:" + resource.getClass());
            }
            registedResource.add(resource);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 释放作用域内登记的全部资源：按登记顺序依次调用各资源的close，单个资源的释放异常只记录日志；
     * 释放后清空登记集合，重复调用为空操作（幂等）。
     */
    public void close() {
        lock.lock();
        try {
            if (released) {
                return;
            }
            released = true;
            for (AutoCloseable autoCloseable : registedResource) {
                try {
                    autoCloseable.close();
                } catch (Exception err) {
                    LOGGER.error("资源释放出错", err);
                }
            }
            registedResource.clear();
        } finally {
            lock.unlock();
        }
    }

}
