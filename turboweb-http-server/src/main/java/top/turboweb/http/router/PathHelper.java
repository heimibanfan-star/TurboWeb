package top.turboweb.http.router;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 处理路径的助手:把各级路径拼成标准路径
 *
 * <p>标准路径的形态是以 / 开头、结尾不含 /,且段间至多一个 /;各级入参按顺序拼接,
 * 空的一级不占位。缺前导斜杠补齐、单个尾斜杠裁除属规整范围;出现连续斜杠视为非法路径直接拒绝。
 * 各级都提不出路径段时结果退化成根路径 /,本类不支持根路由,同样直接拒绝。
 * 问号、通配符等路由语法字符不属路径结构,由各自的语法校验方处理。
 */
public class PathHelper {

    /**
     * 根路径,只作为空入参的形态参与拼接,不可能是合并结果
     */
    private static final String ROOT = "/";

    private PathHelper() {

    }

    /**
     * 合并各级路径并规整为标准路径
     *
     * @param paths 各级路径,按顺序拼接;null 与空串按根路径处理,根路径形态的入参不占位
     * @return 标准路径:以 / 开头、结尾不含 /,段间至多一个 /,至少一段
     * @throws IllegalArgumentException 任一路径含连续的 /,属非法路径;或各级都提不出路径段,
     *                                  结果退化成不受支持的根路径
     */
    public static String mergePath(String... paths) {
        List<String> segments = new ArrayList<>(paths.length);
        for (String path : paths) {
            String standardized = standardize(path);
            if (!ROOT.equals(standardized)) {
                segments.add(standardized);
            }
        }
        if (segments.isEmpty()) {
            // 逐段结构校验已在上方走完,走到这里说明各级都无路径段,即根路径
            throw new IllegalArgumentException("Illegal path, root path routing is not supported: " + Arrays.toString(paths));
        }
        return String.join("", segments);
    }

    /**
     * 校验并规整单条路径,使其具备标准路径的形状
     *
     * @param path 待规整的路径,null 按根路径处理
     * @return 以 / 开头且不含尾斜杠的路径;入参为根路径形态(空串或单个 /)时返回单个 /
     * @throws IllegalArgumentException 路径含连续的 /
     */
    private static String standardize(String path) {
        if (path == null) {
            return ROOT;
        }
        if (path.contains(ROOT + ROOT)) {
            throw new IllegalArgumentException("Illegal path, consecutive slashes are not allowed: " + path);
        }
        if (!path.startsWith(ROOT)) {
            path = ROOT + path;
        }
        if (path.endsWith(ROOT)) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? ROOT : path;
    }
}
