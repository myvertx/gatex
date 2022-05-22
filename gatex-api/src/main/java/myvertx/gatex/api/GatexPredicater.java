package myvertx.gatex.api;

import io.vertx.ext.web.RoutingContext;

/**
 * 断言器
 *
 * @author zbz
 *
 */
public interface GatexPredicater {

    String name();

    boolean test(RoutingContext ctx, Object value);
}
