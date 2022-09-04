package myvertx.gatex.api;

import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyInterceptor;

/**
 * 代理拦截器工厂
 *
 * @author zbz
 */
public interface GatexProxyInterceptorFactory {

    /**
     * @return 工厂名称
     */
    String name();

    /**
     * 创建拦截器
     *
     * @param options 创建拦截器的参数
     */
    ProxyInterceptor create(Vertx vertx, Object options);

}
