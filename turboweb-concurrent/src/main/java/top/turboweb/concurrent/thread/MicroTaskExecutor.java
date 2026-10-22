package top.turboweb.concurrent.thread;

/**
 * 微任务执行器：提交任务执行、不向调用方暴露承载线程的提交契约。
 * <p>
 * 任务与收尾回调均不能为 null，参数校验先于任何资源动作发生；收尾回调保证被调用
 * 且每次提交仅调用一次。调用方无法凭句柄中断或等待任务，只能以收尾回调感知受理结果。
 * </p>
 */
@FunctionalInterface
public interface MicroTaskExecutor {

    /**
     * 收尾回调的触发情形：一次提交有且仅有其中一种收尾。
     */
    enum FinallyType {
        /**
         * 任务已执行完毕：正常返回或抛出异常后，在执行任务的线程中回调。
         */
        EXECUTED,
        /**
         * 未取得资源，任务未执行：在提交线程回调；排队等待超时或被中断时在等待线程回调。
         */
        REJECTED,
        /**
         * 任务未获受理或承载线程未能启动，任务未执行：在提交线程中、原异常抛出前回调。
         */
        NOT_ACCEPT
    }

    /**
     * 任务提交的收尾回调：任务执行完毕、被拒绝、任务未获受理或承载线程未能启动三者必居其一，
     * 每次提交保证被调用且仅调用一次。
     */
    @FunctionalInterface
    interface FinallyHandler {

        /**
         * 收尾时调用。
         *
         * @param finallyType 本次收尾对应的情形
         */
        void onFinally(FinallyType finallyType);
    }

    /**
     * 提交任务执行，不返回承载线程。
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    void submit(Runnable task, FinallyHandler finallyHandler);
}