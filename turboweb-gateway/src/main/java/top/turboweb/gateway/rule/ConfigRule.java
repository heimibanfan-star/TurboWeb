package top.turboweb.gateway.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.turboweb.commons.exception.TurboDuplicateException;
import top.turboweb.commons.struct.trie.PathTrie;
import top.turboweb.commons.struct.trie.PatternPathTrie;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 配置的规则
 */
public class ConfigRule implements Rule {

    private static final Logger log = LoggerFactory.getLogger(ConfigRule.class);
    private final AtomicBoolean used = new AtomicBoolean(false);
    private final PathTrie<String> pathTrie = new PatternPathTrie<>();


    @Override
    public String getServiceName(String path) {
        if (!used.get()) {
            throw new IllegalStateException("The rules have not been used");
        }
        Set<String> names = pathTrie.patternMatch(path);
        if (names.size() > 1) {
            throw new TurboDuplicateException("There are multiple services that match the path: " + path + ": " + names);
        }
        return names.isEmpty() ? null : names.iterator().next();
    }

    @Override
    public boolean isUsed() {
        return used.compareAndSet(false, true);
    }

    /**
     * 添加规则
     *
     * @param pattern       路径模式
     * @param serviceName 服务名
     * @return 规则
     */
    public ConfigRule addRule(String pattern, String serviceName) {
        if (!used.compareAndSet(false, false)) {
            log.warn("The rules have been used and cannot be modified");
            return this;
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("The pattern cannot be empty");
        }
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalArgumentException("The serviceName cannot be empty");
        }
        pathTrie.insert(pattern, serviceName);
        return this;
    }
}
