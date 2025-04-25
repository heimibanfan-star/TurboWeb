package org.turbo.web.core.http.middleware;

import jakarta.validation.constraints.NotBlank;
import org.turbo.web.core.http.context.HttpContext;
import org.turbo.web.core.http.request.HttpInfoRequest;

import java.lang.management.*;
import java.util.*;

/**
 * 服务器信息的中间件
 */
public class ServerInfoMiddleware extends Middleware {

    private String requestPath = "/turboWeb/serverInfo";

    @Override
    public Object invoke(HttpContext ctx) {
        HttpInfoRequest request = ctx.getRequest();
        if (!request.getUri().startsWith(requestPath)) {
            return ctx.doNext();
        }
        return handleServerInfo(ctx);
    }

    private Object handleServerInfo(HttpContext ctx) {
        QueryCon queryCon = ctx.loadValidQuery(QueryCon.class);
        return switch (queryCon.getType()) {
            case "memory" -> ctx.json(Map.of(
                    "type", "memory",
                    "info", getMemoryInfo(),
                    "code", "success"
            ));
            case "thread" -> ctx.json(Map.of(
                    "type", "thread",
                    "info", getThreadInfo(),
                    "code", "success"
            ));
            case "gc" -> ctx.json(Map.of(
                    "type", "gc",
                    "info", getGcInfo(),
                    "code", "success"
            ));
            default -> ctx.json(Map.of(
                    "code", "error",
                    "message", "type不支持"
            ));
        };
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public static class QueryCon {
        @NotBlank(message = "type不能为空")
        private String type;

        public void setType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    /**
     * 获取内存信息
     *
     * @return 内存信息
     */
    private Map<String, ?> getMemoryInfo() {
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        return Map.of(
                "heap", getMemory(memoryPoolMXBeans, MemoryType.HEAP),
                "nonHeap", getMemory(memoryPoolMXBeans, MemoryType.NON_HEAP),
                "nio", getNioMemory(ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
        );
    }

    /**
     * 获取内存信息
     *
     * @param memoryPoolMXBeans 内存池相关的bean
     * @param type              内存类型
     * @return 内存信息
     */
    private List<Map<String, ?>> getMemory(List<MemoryPoolMXBean> memoryPoolMXBeans, MemoryType type) {
        List<Map<String, ?>> infos = new ArrayList<>(memoryPoolMXBeans.size());
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            if (memoryPoolMXBean.getType() == type) {
                Map<String, Object> info = new HashMap<>(4);
                info.put("name", memoryPoolMXBean.getName());
                info.put("used", memoryPoolMXBean.getUsage().getUsed());
                info.put("max", memoryPoolMXBean.getUsage().getMax());
                info.put("committed", memoryPoolMXBean.getUsage().getCommitted());
                infos.add(info);
            }
        }
        return infos;
    }

    private List<Map<String, ?>> getNioMemory(List<BufferPoolMXBean> bufferPoolMXBeans) {
        List<Map<String, ?>> infos = new ArrayList<>(bufferPoolMXBeans.size());
        for (BufferPoolMXBean bufferPoolMXBean : bufferPoolMXBeans) {
            Map<String, Object> info = new HashMap<>(3);
            info.put("name", bufferPoolMXBean.getName());
            info.put("used", bufferPoolMXBean.getMemoryUsed());
            info.put("capacity", bufferPoolMXBean.getTotalCapacity());
            infos.add(info);
        }
        return infos;
    }

    /**
     * 获取线程信息
     *
     * @return 线程信息
     */
    private Map<String, ?> getThreadInfo() {
        Map<String, Object> map = new HashMap<>(2);
        Map<Thread.State, Integer> stateCount = new HashMap<>(6);
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(
                threadMXBean.isCurrentThreadCpuTimeSupported(),
                threadMXBean.isThreadCpuTimeSupported()
        );
        List<Map<String, ?>> infos = new ArrayList<>(threadInfos.length);
        for (ThreadInfo threadInfo : threadInfos) {
            Map<String, Object> info = new HashMap<>(6);
            info.put("name", threadInfo.getThreadName());
            info.put("id", threadInfo.getThreadId());
            Thread.State state = threadInfo.getThreadState();
            if (stateCount.containsKey(state)) {
                stateCount.put(state, stateCount.get(state) + 1);
            } else {
                stateCount.put(state, 1);
            }
            info.put("state", state);
            info.put("blockedTime", threadInfo.getBlockedTime());
            info.put("waitedTime", threadInfo.getWaitedTime());
            info.put("lockName", threadInfo.getLockName());
            infos.add(info);
        }
        infos.sort(Comparator.comparing(o -> o.get("name").toString()));
        map.put("stateCount", stateCount);
        map.put("infos", infos);
        return map;
    }

    /**
     * 获取GC信息
     *
     * @return GC信息
     */
    private List<Map<String, ?>> getGcInfo() {
        List<Map<String, ?>> infos = new ArrayList<>();
        List<GarbageCollectorMXBean> gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcMXBean : gcMXBeans) {
            Map<String, Object> info = new HashMap<>(3);
            info.put("name", gcMXBean.getName());
            info.put("count", gcMXBean.getCollectionCount());
            info.put("time", gcMXBean.getCollectionTime());
            infos.add(info);
        }
        return infos;
    }
}
