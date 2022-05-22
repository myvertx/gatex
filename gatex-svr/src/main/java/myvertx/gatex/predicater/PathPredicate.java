package myvertx.gatex.predicater;

import java.util.List;

import io.vertx.ext.web.RoutingContext;
import myvertx.gatex.api.GatexPredicater;

public class PathPredicate implements GatexPredicater {

    @Override
    public String name() {
        return "path";
    }

    @Override
    public boolean test(final RoutingContext ctx, final Object value) {
        final String path       = ctx.request().path();
        final String methodName = ctx.request().method().name();
        if (value instanceof final String expr) {
            return startsWith(methodName, path, expr);
        } else if (value instanceof final List<?> list) {
            for (final Object expr : list) {
                if (startsWith(methodName, path, (String) expr)) {
                    return true;
                }
            }
            return false;
        }
        throw new IllegalArgumentException(name() + "的值只允许是String或String[]类型");
    }

    /**
     *
     * 判断path是否以expr开始
     *
     * @param method 请求的方法
     * @param path   当前路由的路径
     * @param expr   要比较的表达式
     *
     */
    private boolean startsWith(final String methodName, final String path, String expr) {
        if (expr.startsWith("[")) {
            final String[] pathSplit  = expr.split("]");
            final String   methodsStr = pathSplit[0];
            final String[] methods    = methodsStr.split(",");
            boolean        bSearched  = false;
            for (final String method : methods) {
                if (method.equalsIgnoreCase(methodName)) {
                    bSearched = true;
                    break;
                }
            }
            if (!bSearched) {
                return false;
            }
            expr = pathSplit[1];
        }

        return path.startsWith(expr);
    }

}
