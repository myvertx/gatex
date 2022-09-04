package myvertx.gatex.api;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;

/**
 * 匹配器
 *
 * @author zbz
 */
public interface GatexMatcher {

    /**
     * @return 匹配器的名称
     */
    String name();

    /**
     * 添加匹配器
     *
     * @param route 路由
     * @param value 匹配器的参数的值
     */
    void addMatcher(Vertx vertx, Route route, Object value);

}
