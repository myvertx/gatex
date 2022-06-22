package myvertx.gatex.api;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * 过滤器工厂
 *
 * @author zbz
 */
public interface GatexFilterFactory {

    /**
     * @return 过滤器工厂名称
     */
    String name();

    /**
     * 创建过滤器
     *
     * @param filterParam 创建过滤器的参数
     */
    Handler<RoutingContext> create(Object filterParam);

}