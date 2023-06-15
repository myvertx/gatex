package myvertx.gatex.handler;

import io.vertx.core.Future;
import myvertx.gatex.drools.fact.RequestFact;

/**
 * 重新路由的处理器
 *
 * @author zbz
 */
public interface RerouteHandler {
    /**
     * 判断是否需要重新路由
     *
     * @param sRequestBody  之前请求的body
     * @param sResponseBody 之前请求响应回来的body
     * @return 是否需要重新路由
     */
    boolean isReroute(String sRequestBody, String sResponseBody);

    /**
     * 设置重新路由的请求
     *
     * @param requestFact 要请求的详情
     * @return 请求的详情
     */
    default Future<RequestFact> setRequestOfReroute(RequestFact requestFact) {
        return Future.succeededFuture(requestFact);
    }

}
