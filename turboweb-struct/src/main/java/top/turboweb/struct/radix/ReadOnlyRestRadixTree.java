package top.turboweb.struct.radix;

/**
 * 只读 REST 基数树视图：内部持有写实例，match 委托过去，
 * 写侧后续 put / remove 对本视图可见（活视图，非快照）。
 * 仅能经 {@link WriteAbleRestRadixTree#toReadView()} 获得。
 * <p>非并发安全。
 *
 * @param <O> 值类型
 */
class ReadOnlyRestRadixTree<O> implements RestRadixTree<O> {

    private final WriteAbleRestRadixTree<O> delegate;

    ReadOnlyRestRadixTree(WriteAbleRestRadixTree<O> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MatchResult<O> match(String path) {
        return delegate.match(path);
    }
}
