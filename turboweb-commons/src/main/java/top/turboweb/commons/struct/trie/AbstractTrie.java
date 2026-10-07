package top.turboweb.commons.struct.trie;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 抽象前缀树（Trie）实现基类。
 *
 * <p>该类提供了通用的 Trie 结构增删查改逻辑，
 * 包括节点的插入、删除、精确查找等功能。
 * <br>子类可通过 {@link #handleSubKey(String)} 与 {@link #splitString()} 方法
 * 自定义键的处理规则与分隔符。
 *
 * <p>该类的实现特点：
 * <ul>
 *   <li>线程不安全（如需并发访问请在外层加锁或使用并发包装）。</li>
 *   <li>节点数量通过 {@code size} 维护，包含所有具有值的节点。</li>
 *   <li>子类可实现不同的路径语义，如 URL Trie、Pattern Trie 等。</li>
 * </ul>
 *
 * @param <T> 节点存储的值类型
 * @param <M> 匹配方法返回的结果类型（由具体实现定义）
 */
public abstract class AbstractTrie <T, M> implements Trie<T, M>{

    /** 根节点，不存储实际值。 */
    protected final Node<T> root = new Node<>(null, null);

    /** 当前 Trie 中包含的节点数量（仅统计具有非空值的节点）。 */
    private int size = 0;

    /**
     * 节点的详细信息，用于保存子类自定义的键处理结果。
     *
     * <p>例如在 URL Trie 中，可能需要将 "{id}" 转换为 ":id" 等规范化子键。
     */
    protected static class Details {
        private final String newSubKey;

        protected Details(String newSubKey) {
            this.newSubKey = newSubKey;
        }

        /**
         * 获取新的子key
         * @return 新的子key
         */
        public String newSubKey() {
            return newSubKey;
        }
    }


    /**
     * 前缀树节点。
     *
     * @param <T> 节点值类型
     */
    protected static class Node<T> {
        private final String key;
        private final Details details;
        private T val;
        private final Map<String, Node<T>> subNodes = new HashMap<>();

        protected Node(String key, Details details) {
            this.key = key;
            this.details = details;
        }

        /**
         * 获取指定key的子节点
         * @param key 子节点的key
         * @return 子节点
         */
        protected Node<T> get(String key) {
            return subNodes.get(key);
        }

        /**
         * 添加子节点
         * @param key 子节点的key
         * @param node 子节点
         */
        protected void put(String key, Node<T> node) {
            subNodes.put(key, node);
        }


        /**
         * 删除子节点
         * @param key 子节点的key
         */
        protected void remove(String key) {
            subNodes.remove(key);
        }

        /**
         * 设置节点的值
         * @param val 节点的值
         */
        public void setValue(T val) {
            this.val = val;
        }

        /**
         * 获取节点的值
         * @return 节点的值
         */
        protected T value() {
            return val;
        }

        /**
         * 获取节点的详细信息
         * @param type 节点的详细信息的类型
         * @param <D> 节点的详细信息的类型
         * @return 节点的详细信息
         */
        protected <D extends Details> D details(Class<D> type) {
            if (details == null) {
                return null;
            }
            return type.cast(details);
        }

        /**
         * 获取节点的详细信息
         * @return 节点的详细信息
         */
        protected Details details() {
            return details;
        }

        /**
         * 获取节点的子节点的key
         * @return 子节点的key
         */
        protected Set<String> subKeys() {
            return subNodes.keySet();
        }

        /**
         * 获取节点的key
         * @return 节点的key
         */
        protected String key() {
            return key;
        }

        /**
         * 获取节点的子节点
         * @return 子节点
         */
        protected Map<String, Node<T>> subNodes() {
            return subNodes;
        }
    }

    /**
     * 插入键值对。
     *
     * <p>若 {@code overwrite = false} 且目标键已存在，将抛出异常。
     * 分割符与子键处理由子类实现决定。
     * <br>每段先经 {@link #handleSubKey(String)} 归一化，查重、查找与存放统一使用归一化后的键。
     */
    @Override
    public void insert(String key, T value, boolean overwrite) {
        // 分割key
        String[] strs = splitKey(key);
        // 判断key是否为空
        if (strs.length == 0) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        // 迭代添加节点
        int lastIndex = strs.length - 1;
        Node<T> currentNode = root;
        for (int i = 0; i <= lastIndex; i++) {
            String str = strs[i];
            // 判断是否是空字符
            if (str.isEmpty()) {
                throw new IllegalArgumentException("subKey cannot be empty");
            }
            // 先归一化再查找：map 的键就是归一化后的键，判重必须用同一个键（修 F1）
            Details details = handleSubKey(str);
            if (details != null && details.newSubKey() != null && !details.newSubKey().isEmpty()) {
                str = details.newSubKey();
            }
            Node<T> node = currentNode.get(str);
            // 判断是否是最后一个字符
            if (i == lastIndex) {
                // 重复性判断
                if (!overwrite && node != null && node.value() != null) {
                    throw new IllegalArgumentException("key already exists");
                }
                if (node == null) {
                    node = new Node<>(str, details);
                    size++;
                } else {
                    if (node.value() == null) {
                        size++;
                    }
                }
                node.setValue(value);
                currentNode.put(str, node);
            } else {
                if (node == null) {
                    node = new Node<>(str, details);
                    currentNode.put(str, node);
                }
                currentNode = node;
            }
        }
    }

    @Override
    public void insert(String key, T value) {
        this.insert(key, value, false);
    }

    /**
     * 精确获取键对应的值。
     *
     * <br>每段与 {@link #insert} 同源：先经 {@link #handleSubKey(String)} 归一化，再按归一化后的键下钻。
     *
     * @param key 键
     * @return 匹配到的值；若不存在则返回 {@code null}
     */
    @Override
    public T get(String key) {
        if (size == 0) {
            return null;
        }
        // 分割key
        String[] strs = splitKey(key);
        // 判断key是否能被成功分割
        if (strs.length == 0) {
            return null;
        }
        // 迭代寻找节点
        Node<T> currentNode = root;
        for (String str : strs) {
            // 出现空字符说明节点根本不存在
            if (str.isEmpty()) {
                return null;
            }
            // 先归一化再查：map 的键就是归一化后的键，原始段（如 {id}）本身不是键（修 F1b）
            Details details = handleSubKey(str);
            if (details != null && details.newSubKey() != null && !details.newSubKey().isEmpty()) {
                str = details.newSubKey();
            }
            // 获取节点
            Node<T> node = currentNode.get(str);
            if (node == null) {
                return null;
            }
            currentNode = node;
        }
        // 判断是否找到节点
        return currentNode != root? currentNode.value(): null;
    }

    @Override
    public boolean exists(String key) {
        return get(key) != null;
    }

    @Override
    public Set<String> roots() {
        return root.subKeys();
    }

    @Override
    public int size() {
        return size;
    }

    /**
     * 删除指定键对应的节点。
     * <p>若目标节点存在子节点且无值，将递归清理空分支。
     */
    @Override
    public void delete(String key) {
        // 如果节点数量为0，放弃删除
        if (size == 0) {
            return;
        }
        // 创建栈
        LinkedList<Node<T>> stack = new LinkedList<>();
        // 分割路径
        String[] strs = splitKey(key);
        if (strs.length == 0) {
            return;
        }
        // 迭代寻找删除的路径
        stack.push(root);
        Node<T> current = root;
        for (String str : strs) {
            // 如果字串是空，说明不存在节点
            if (str.isEmpty()) {
                return;
            }
            // 查询当前路径对应的节点
            Node<T> node = current.get(str);
            if (node == null) {
                return;
            }
            stack.push(node);
            current = node;
        }
        // 清空目标节点的值
        Node<T> target = stack.pop();
        if (target.value() == null) {
            return;
        }
        target.setValue(null);
        size--;
        // 迭代清理路径
        while (!stack.isEmpty()) {
            Node<T> parent = stack.pop();
            // 判断当前节点是否还有剩余的节点
            if (target.subKeys().isEmpty() && target.value() == null) {
                parent.remove(target.key());
                target = parent;
            } else {
                break;
            }
        }
    }

    /**
     * 分割路径键。
     *
     * <p>默认去除首尾分隔符并按 {@link #splitString()} 返回的分隔符进行拆分。
     *
     * @param key 原始键
     * @return 拆分后的路径段数组
     */
    protected String[] splitKey(String key) {
        // 字符串合法性校验
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be null or empty");
        }
        String splitStr = splitString();
        if (splitStr == null || splitStr.isEmpty()) {
            throw new IllegalArgumentException("splitString cannot be null or empty");
        }
        // 去除字符串首尾的分隔符
        key = key.trim().replaceAll("^" + Pattern.quote(splitStr) + "|" + Pattern.quote(splitStr) + "$", "");
        // 分割成字符串数组
        return key.split(Pattern.quote(splitStr));
    }

    /**
     * 处理子键。
     *
     * <p>子类可根据需要转换或校验子键，
     * 例如将路径参数 "{id}" 转换为统一标识形式。</p>
     *
     * @param subKey 原始子键
     * @return 处理结果，若无需修改可返回 {@code null}
     */
    protected abstract Details handleSubKey(String subKey);


    /**
     * 返回键的分隔符。
     * <p>例如 URL Trie 中返回 "/"。</p>
     *
     * @return 分隔符字符串
     */
    protected abstract String splitString();
}
