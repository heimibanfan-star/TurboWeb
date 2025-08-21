package top.turboweb.http.middleware.router.container;

/**
 * 处理路径的助手
 */
public class PathHelper {

    private PathHelper() {

    }

    /**
     * 合并路径
     * @param prePath 前置路径
     * @param path 子路径
     * @return 合并后的路径
     */
    public static String mergePath(String prePath, String path) {
        // 处理类路径
        if (prePath == null || prePath.isEmpty() || "/".equals(prePath)) {
            prePath = "";
        } else if (!prePath.startsWith("/")) {
            prePath = "/" + prePath;
        }
        if (prePath.endsWith("/")) {
            prePath = prePath.substring(0, prePath.length() - 1);
        }
        // 处理子路径
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return  prePath.isEmpty() ? "/" : prePath;
        }
        // 判断是否有/开头
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String allPath = prePath + path;
        allPath = allPath.replaceAll("/+", "/");
        if (allPath.contains("*") || allPath.contains("..")) {
            throw new IllegalArgumentException("路由路径不允许包含*或..");
        }
        return allPath.endsWith("/") ? allPath.substring(0, allPath.length() - 1) : allPath;
    }
}
