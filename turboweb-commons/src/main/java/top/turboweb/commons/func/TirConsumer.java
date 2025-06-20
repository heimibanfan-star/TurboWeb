package top.turboweb.commons.func;

/**
 * 三参数的消费者
 */
@FunctionalInterface
public interface TirConsumer <T, U, V> {

	void accept(T t, U u, V v);
}
