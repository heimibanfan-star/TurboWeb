package top.turboweb.loadbalance;

import top.turboweb.loadbalance.node.DefaultNode;
import top.turboweb.loadbalance.node.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 基于轮询策略的负载均衡器实现。
 *
 * <p>每个服务维护一个节点列表，负载均衡器按顺序轮询选择节点。
 * 支持服务节点的动态添加、删除和重置。
 *
 * <p>线程安全：
 * - 使用 {@link ReentrantReadWriteLock} 管理服务节点的并发读写。
 * - 节点索引使用 {@link AtomicInteger} 保证轮询操作原子性。
 *
 * <p>注意：
 * - 节点 URL 会在添加时去除协议部分（如 "http://"）和末尾斜杠。
 */
public class RibbonLoadBalancer implements LoadBalancer {

    /**
     * 服务对应的节点集合及轮询索引。
     */
    private static final class ServiceNodes {
        // 地址列表
        final List<Node> nodes = new ArrayList<>();
        // 用于校验地址是否重复
        final Set<String> urls = new HashSet<>();
        // 当前节点的索引
        final AtomicInteger index = new AtomicInteger(0);
    }

    /** 所有服务及节点 */
    private final Map<String, ServiceNodes> services = new ConcurrentHashMap<>();
    /** 服务节点访问锁 */
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    /**
     * 添加服务及其节点。
     *
     * @param serviceName 服务名
     * @param addresses   节点地址数组
     */
    @Override
    public void addServices(String serviceName, String... addresses) {
        readWriteLock.writeLock().lock();
        try {
            ServiceNodes serviceNodes = services.get(serviceName);
            if (serviceNodes == null) {
                serviceNodes = new ServiceNodes();
                services.put(serviceName, serviceNodes);
            }
            for (String url : addresses) {
                if (!serviceNodes.urls.contains(url)) {
                    serviceNodes.urls.add(url);
                    serviceNodes.nodes.add(new DefaultNode(url));
                }
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 添加服务及其节点（支持 Set 集合）。
     *
     * <p>会自动去掉协议和末尾斜杠。
     *
     * @param serviceName 服务名
     * @param addresses   节点地址集合
     */
    @Override
    public void addServices(String serviceName, Set<String> addresses) {
        // 处理地址
        String[] urlsArray = addresses.stream().map(address -> {
            Objects.requireNonNull(address);
            // 去除协议部分
            if (address.contains("://")) {
                address = address.substring(address.indexOf("://") + 3);
            }
            // 取出末尾所有的/
            return address.replaceAll("/$", "");
        }).toArray(String[]::new);
        addServices(serviceName, urlsArray);
    }

    /**
     * 删除指定服务的所有节点。
     *
     * @param serviceName 服务名
     */
    @Override
    public void removeServices(String serviceName) {
        readWriteLock.writeLock().lock();
        try {
            services.remove(serviceName);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 删除指定服务的单个节点。
     *
     * @param serviceName 服务名
     * @param nodeAddress 节点地址
     */
    @Override
    public void removeServiceNode(String serviceName, String nodeAddress) {
        readWriteLock.writeLock().lock();
        try {
            ServiceNodes serviceNodes = services.get(serviceName);
            if (serviceNodes != null && serviceNodes.urls.contains(nodeAddress)) {
                Iterator<Node> iterator = serviceNodes.nodes.iterator();
                while (iterator.hasNext()) {
                    Node node = iterator.next();
                    if (Objects.equals(node.url(), nodeAddress)) {
                        iterator.remove();
                        break;
                    }
                }
                // 重置游标
                serviceNodes.index.set(0);
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 按轮询策略返回指定服务的一个节点。
     *
     * @param serviceName 服务名
     * @return 选中的节点，如果服务不存在或无可用节点则返回 {@code null}
     */
    @Override
    public Node loadBalance(String serviceName) {
        // 获取写锁
        readWriteLock.readLock().lock();
        try {
            ServiceNodes serviceNodes = services.get(serviceName);
            if (serviceNodes == null || serviceNodes.nodes.isEmpty()) {
                return null;
            }
            int index = serviceNodes.index.get();
            Node node = serviceNodes.nodes.get(index);
            for (; ; ) {
                if (index == serviceNodes.nodes.size() - 1) {
                    if (serviceNodes.index.compareAndSet(index, 0)) {
                        break;
                    } else {
                        index = serviceNodes.index.get();
                    }
                }  else {
                    if (serviceNodes.index.compareAndSet(index, index + 1)) {
                        break;
                    } else {
                        index = serviceNodes.index.get();
                    }
                }
            }
            return node;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * 重置所有服务节点信息。
     *
     * <p>清空现有节点并重新注册提供的节点。
     *
     * @param servicesNodes 服务名到节点地址集合的映射
     */
    @Override
    public void resetServiceNodes(Map<String, Set<String>> servicesNodes) {
        readWriteLock.writeLock().lock();
        try {
            // 清空所有节点
            services.clear();
            // 重新注册节点
            servicesNodes.forEach(this::addServices);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }
}
