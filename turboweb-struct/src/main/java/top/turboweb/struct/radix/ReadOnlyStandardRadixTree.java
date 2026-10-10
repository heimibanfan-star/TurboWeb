package top.turboweb.struct.radix;

import java.util.Set;

/**
 * 只读基数树视图：内部持有写实例，三个读方法委托过去，
 * 写侧后续 put / remove 对本视图可见（活视图，非快照）。
 * 仅能经 {@link WriteAbleStandardRadixTree#toReadView()} 获得。
 * <p>非并发安全。
 *
 * @param <O> 值类型
 */
class ReadOnlyStandardRadixTree<O> implements MutiRadixTree<O> {

    private final WriteAbleStandardRadixTree<O> delegate;

    ReadOnlyStandardRadixTree(WriteAbleStandardRadixTree<O> delegate) {
        this.delegate = delegate;
    }

    @Override
    public O get(CharSequence key) {
        return delegate.get(key);
    }

    @Override
    public boolean exist(CharSequence key) {
        return delegate.exist(key);
    }

    @Override
    public Set<O> match(CharSequence key) {
        return delegate.match(key);
    }
}
