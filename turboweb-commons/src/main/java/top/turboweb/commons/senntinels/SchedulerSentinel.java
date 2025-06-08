package top.turboweb.commons.senntinels;

/**
 * 调度器的哨兵
 */
public interface SchedulerSentinel {

    /**
     * 提交任务
     *
     * @param runnable 任务
     * @return boolean 是否提交成功
     */
    boolean submitTask(Runnable runnable);
}
