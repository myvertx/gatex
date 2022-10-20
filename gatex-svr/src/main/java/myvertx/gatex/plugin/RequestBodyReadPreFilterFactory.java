package myvertx.gatex.plugin;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexFilterFactory;

/**
 * 读取Body的前置过滤器的工厂
 *
 * @author zbz
 */
@Slf4j
public class RequestBodyReadPreFilterFactory implements GatexFilterFactory {

    @Override
    public String name() {
        return "requestBodyRead";
    }

    @Override
    public Handler<RoutingContext> create(Vertx vertx, Object options) {
        log.info("RequestBodyReadPreFilterFactory.create: {}", options);
        return BodyHandler.create();
    }

}
