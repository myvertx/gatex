package myvertx.gatex.handler;

import io.vertx.core.Future;

/**
 * 重新路由的处理器
 *
 * @author zbz
 */
public interface RerouteHandler {
    /**
     * 判断是否需要重新路由
     *
     * @param sResponseBody 之前请求响应回来的body
     * @return 是否需要重新路由
     */
    boolean isReroute(String sResponseBody);

    /**
     * 获取重新路由的请求body
     *
     * @param sOriginRequestBody 原始的请求body(默认重新路由的请求body就是原始路由的请求body)
     * @return 重新路由的请求body
     */
    default Future<String> getRerouteRequestBody(String sOriginRequestBody) {
        return Future.succeededFuture(sOriginRequestBody);
    }

    /**
     * 获取重新路由的请求路径
     */
    String getReroutePath();
}
