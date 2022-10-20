package myvertx.gatex.plugin;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexFilterFactory;
import rebue.wheel.vertx.web.RequestBodyCachedHandler;

/**
 * 缓存Body的前置过滤器的工厂
 *
 * @author zbz
 */
@Slf4j
public class RequestBodyCachedPreFilterFactory implements GatexFilterFactory {

    @Override
    public String name() {
        return "requestBodyCached";
    }

    @Override
    public Handler<RoutingContext> create(Vertx vertx, Object options) {
        log.info("RequestBodyCachedPreFilterFactory.create: {}", options);
        return new RequestBodyCachedHandler();
    }

}
