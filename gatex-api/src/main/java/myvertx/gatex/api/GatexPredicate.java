package myvertx.gatex.api;

import io.vertx.ext.web.RoutingContext;

public interface GatexPredicate {

    String name();

    boolean test(RoutingContext ctx, Object value);
}
