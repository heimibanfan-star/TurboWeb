package top.turboweb.commons.struct.trie;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 前缀树抽象类
 */
public abstract class AbstractTrie <T> implements Trie<T>{

    protected final Node<T> root = new Node<>(null, null);

    /**
     * 节点的详细信息
     */
    protected static class Details {

    }

    private int size = 0;

    /**
     * 前缀树节点
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
            Node<T> node = currentNode.get(str);
            // 判断是否是最后一个字符
            if (i == lastIndex) {
                // 重复性判断
                if (!overwrite && node != null && node.value() != null) {
                    throw new IllegalArgumentException("key already exists");
                }
                if (node == null) {
                    node = new Node<>(str, handleSubKey(str));
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
                    node = new Node<>(str, handleSubKey(str));
                    currentNode.put(str, node);
                }
                currentNode = node;
            }
        }
    }


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
     * 分割key
     * @param key key
     * @return 分割后的key
     */
    private String[] splitKey(String key) {
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

    @Override
    public Map<String, T> all() {
        // 结果的集合
        Map<String, T> result = new HashMap<>();
        // 创建栈
        LinkedList<String> keyStack = new LinkedList<>();
        LinkedList<Queue<Node<T>>> nodeStack = new LinkedList<>();
        // 将所有的根节点的值添加到队列中
        Queue<Node<T>> queue = new LinkedList<>(root.subNodes().values());
        nodeStack.push(queue);
        // 遍历所有的节点
        while (!nodeStack.isEmpty()) {
            Queue<Node<T>> nodeQueue = nodeStack.peek();
            if (nodeQueue.isEmpty()) {
                // 出栈
                nodeStack.pop();
                keyStack.pop();
            } else {
                Node<T> node = nodeQueue.poll();
                // 判断是否是值节点
                if (node.value() != null) {
                    StringBuilder key = new StringBuilder();
                    for (String k : keyStack) {
                        key.append(k).append(splitString());
                    }
                    key.append(node.key());
                    // 存储结果
                    result.put(key.toString(), node.value());
                }
                // 判断是否有子节点
                if (!node.subKeys().isEmpty()) {
                    queue = new LinkedList<>(node.subNodes().values());
                    nodeStack.push(queue);
                    keyStack.push(node.key());
                }
            }
        }
        return result;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            final Iterator<Map.Entry<String, T>> iterator = all().entrySet().iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return iterator.next().getValue();
            }
        };
    }

    /**
     * 处理子key
     * @param subKey 子key
     * @return 处理后的详细信息，若不处理返回null
     */
    protected abstract Details handleSubKey(String subKey);


    /**
     * 获取分隔符
     * @return 分隔符
     */
    protected abstract String splitString();
}
