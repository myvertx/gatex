package myvertx.gatex.api;

import io.vertx.ext.web.RoutingContext;

/**
 * 断言器
 *
 * @author zbz
 *
 */
public interface GatexPredicater {

    /**
     * @return 断言器名称
     */
    String name();

    /**
     * 判断是否通过
     *
     * @param ctx   路由上下文
     * @param value 要判断的值
     *
     * @return 是否通过
     */
    boolean test(RoutingContext ctx, Object value);
}
