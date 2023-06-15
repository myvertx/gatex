package myvertx.gatex.api;

import com.google.inject.Injector;
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
     * @param vertx    vertx实例
     * @param injector 注入器
     * @param dst      目的地的配置
     * @param options  代理拦截器的配置选项
     * @return 拦截器
     */
    ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options);

}
