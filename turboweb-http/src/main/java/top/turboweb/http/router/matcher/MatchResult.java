package top.turboweb.http.router.matcher;

import top.turboweb.http.router.definition.RouterMethodDefinition;

/**
 * 匹配的结果
 */
public class MatchResult {

    private final RouterMethodDefinition definition;
    private final String matchType;

    public MatchResult(RouterMethodDefinition definition, String matchType) {
        this.definition = definition;
        this.matchType = matchType;
    }

    public RouterMethodDefinition getDefinition() {
        return definition;
    }

    public String getMatchType() {
        return matchType;
    }
}
