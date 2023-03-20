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

    default Future<String> getRerouteRequestBody(String sOriginRequestBody) {
        return Future.succeededFuture(sOriginRequestBody);
    }

}
