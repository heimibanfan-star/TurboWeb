package top.turboweb.commons.utils.thread;

/**
 * 线程断言
 */
public class ThreadAssert {

    /**
     * 断言当前线程是虚拟线程
     */
    public static void assertIsVirtualThread() {
        if (!Thread.currentThread().isVirtual()) {
            throw new IllegalStateException("This method must be called from a virtual thread");
        }
    }
}
