package top.turboweb.loadbalance.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboDuplicateException;
import top.turboweb.commons.struct.trie.PatternUrlTrie;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 节点规则管理器
 */
public class NodeRuleManager implements RuleManager {

    private static final Logger log = LoggerFactory.getLogger(NodeRuleManager.class);
    private final AtomicBoolean used = new AtomicBoolean(false);
    private final PatternUrlTrie<RuleDetail> pathTrie = new PatternUrlTrie<>();
    private final boolean localPre;

    public NodeRuleManager(boolean localPre) {
        this.localPre = localPre;
    }

    public NodeRuleManager() {
        this(true);
    }

    private record ServiceResult(RuleDetail local, RuleDetail remote) {
    }

    /**
     * 获取服务名
     *
     * @param path         请求路径
     * @return 服务名
     */
    private ServiceResult doGetService(String path) {
        if (!used.get()) {
            throw new IllegalStateException("The rules have not been used");
        }
        Set<RuleDetail> details = pathTrie.match(path);
        if (details.size() > 2) {
            throw new TurboDuplicateException("There are multiple rules that match the path:" + path + ", services:" + details.stream().map(RuleDetail::serviceName).toList());
        }
        if (details.size() == 2) {
            RuleDetail local = null;
            RuleDetail remote = null;
            for (RuleDetail detail : details) {
                if (detail.local()) {
                    local = detail;
                } else {
                    remote = detail;
                }
            }
            return new ServiceResult(local, remote);
        }
        if (details.size() == 1) {
            RuleDetail detail = details.iterator().next();
            return new ServiceResult(detail.local()? detail : null, detail.local()? null : detail);
        }
        return new ServiceResult(null, null);
    }

    @Override
    public RuleDetail getLocalService(String path) {
        ServiceResult serviceResult = doGetService(path);
        return serviceResult.local();
    }

    @Override
    public RuleDetail getRemoteService(String path) {
        ServiceResult serviceResult = doGetService(path);
        return serviceResult.remote();
    }

    @Override
    public RuleDetail getService(String path) {
        ServiceResult serviceResult = doGetService(path);
        if (localPre && serviceResult.local() != null) {
            return serviceResult.local();
        }
        return serviceResult.remote();
    }

    @Override
    public boolean isUsed() {
        return used.compareAndSet(false, true);
    }

    /**
     * 添加规则
     *
     * @param pattern     路径模式
     * @param serviceName 服务名
     * @return this
     */
    public NodeRuleManager addRule(String pattern, String serviceName) {
        return addRule(pattern, serviceName, null, null);
    }

    /**
     * 添加规则
     *
     * @param pattern           路径模式
     * @param serviceExpression 服务表达式
     * @param rewRegix          重写路径模式
     * @param rewTar            重写目标
     * @return this
     */
    public NodeRuleManager addRule(String pattern, String serviceExpression, String rewRegix, String rewTar) {
        if (!used.compareAndSet(false, false)) {
            log.warn("The rules have been used and cannot be modified");
            return this;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("The pattern cannot be empty");
        }
        if (serviceExpression == null || serviceExpression.isEmpty()) {
            throw new IllegalArgumentException("The serviceExpression cannot be empty");
        }
        if (rewRegix == null) {
            rewRegix = "";
        }
        if (rewTar == null) {
            rewTar = "";
        }
        URI uri = URI.create(serviceExpression);
        String protocol = uri.getScheme();
        String serviceName = uri.getHost();
        String extPath = uri.getPath();
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalArgumentException("Expressions are not legal:" + serviceExpression);
        }
        if (protocol == null || protocol.isEmpty()) {
            protocol = "http";
        }
        if (extPath == null || "/".equals(extPath)) {
            extPath = "";
        }
        extPath = extPath.replaceAll("/$", "");
        // 已服务名分割协议与扩展路径
        RuleDetail detail = new RuleDetail(
                serviceName,
                rewRegix,
                rewTar,
                "local".equals(serviceExpression),
                RuleDetail.Protocol.getProtocol(protocol),
                extPath
        );
        pathTrie.insert(pattern, detail);
        return this;
    }
}
