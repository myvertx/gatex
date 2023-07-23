package myvertx.gatex.api;

import io.vertx.ext.web.RoutingContext;

/**
 * 断言器
 *
 * @author zbz
 */
public interface GatexPredicate {

    /**
     * 判断是否通过
     *
     * @param ctx 路由上下文
     * @return 测试是否通过，如通过就继续往下处理，否则中断不往下处理
     */
    boolean test(RoutingContext ctx);
}
