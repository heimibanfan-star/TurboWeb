package top.turboweb.concurrent.thread;

/**
 * 微任务执行器：提交任务执行、不向调用方暴露承载线程的提交契约。
 * <p>
 * 任务与收尾回调均不能为 null，参数校验先于任何资源动作发生；收尾回调保证被调用
 * 且每次提交仅调用一次。调用方无法凭句柄中断或等待任务，只能以收尾回调感知受理结果。
 * </p>
 */
@FunctionalInterface
public interface MicroTaskExecutor extends AutoCloseable {

    /**
     * 收尾回调的执行状态：一次提交有且仅有其中一种收尾。
     */
    enum FinallyType {
        /**
         * 任务已执行完毕：正常返回或抛出异常后，在执行任务的线程中回调。
         */
        EXECUTED,
        /**
         * 任务未获受理、任务未启动或排队等待被放弃，任务未执行：
         * 未取得资源或线程未能启动时在提交线程中、原异常抛出前回调；
         * 排队等待超时或被中断时在本次提交新建的虚拟线程中回调。
         */
        NOT_ACCEPT
    }

    /**
     * 收尾回调信号：携带本次收尾的执行状态与回调发生所在的线程方。
     *
     * @param callbackThread 回调发生所在的线程方：提交线程或本次提交新建的虚拟线程
     * @param finallyType    本次收尾的执行状态
     */
    record FinallySignal(CallbackThread callbackThread, FinallyType finallyType) {

        /**
         * 收尾回调发生所在的线程方。
         */
        public enum CallbackThread {
            /** 提交线程：收尾在 submit/run 返回前同步触发。 */
            SUBMITTER,
            /** 本次提交新建的虚拟线程：承载任务执行或排队等待，收尾异步触发。 */
            WORKER
        }
    }

    /**
     * 任务提交的收尾回调：任务执行完毕与任务未受理二者必居其一，每次提交保证被调用且仅调用一次。
     * 回调自身抛出的异常由执行器就地记账，不向提交方传播、不掩盖任务原始异常，也不影响恰好一次的收尾。
     */
    @FunctionalInterface
    interface FinallyHandler {

        /**
         * 收尾时调用。
         *
         * @param finallySignal 本次收尾的信号：回调发生所在的线程方与执行状态
         */
        void onFinally(FinallySignal finallySignal);
    }

    /**
     * 提交任务执行，不返回承载线程。
     *
     * @param task           获取到资源后执行的任务，不能为 null
     * @param finallyHandler 任务提交的收尾回调，不能为 null
     * @throws NullPointerException task 或 finallyHandler 为 null
     */
    void submit(Runnable task, FinallyHandler finallyHandler);

    /**
     * 释放执行器持有的资源：无须回收资源的实现继承本空实现即可；重复调用为幂等。
     */
    @Override
    default void close() {
    }
}