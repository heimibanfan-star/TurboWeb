package top.turboweb.gateway.loadbalance;

import top.turboweb.commons.exception.TurboDuplicateException;
import top.turboweb.gateway.node.DefaultNode;
import top.turboweb.gateway.node.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 轮询的负载均衡器
 */
public class RibbonLoadBalancer implements LoadBalancer {

    private static final class ServiceNodes {
        // 地址列表
        final List<Node> nodes = new ArrayList<>();
        // 用于校验地址是否重复
        final Set<String> urls = new HashSet<>();
        // 当前节点的索引
        final AtomicInteger index = new AtomicInteger(0);
    }

    private final Map<String, ServiceNodes> services = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    @Override
    public void addServices(String serviceName, String... urls) {
        readWriteLock.writeLock().lock();
        try {
            ServiceNodes serviceNodes = services.get(serviceName);
            if (serviceNodes == null) {
                serviceNodes = new ServiceNodes();
                services.put(serviceName, serviceNodes);
            }
            for (String url : urls) {
                if (!serviceNodes.urls.contains(url)) {
                    serviceNodes.urls.add(url);
                    serviceNodes.nodes.add(new DefaultNode(url, false));
                }
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void addServices(String serviceName, Set<String> urls) {
        String[] urlsArray = urls.toArray(new String[0]);
        addServices(serviceName, urlsArray);
    }

    @Override
    public void removeServices(String serviceName) {
        readWriteLock.writeLock().lock();
        try {
            services.remove(serviceName);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public void removeServiceNode(String serviceName, String nodeUrl) {
        readWriteLock.writeLock().lock();
        try {
            ServiceNodes serviceNodes = services.get(serviceName);
            if (serviceNodes != null && serviceNodes.urls.contains(nodeUrl)) {
                Iterator<Node> iterator = serviceNodes.nodes.iterator();
                while (iterator.hasNext()) {
                    Node node = iterator.next();
                    if (Objects.equals(node.url(), nodeUrl)) {
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
