package myvertx.gatex.plugin;

import com.google.inject.Injector;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;
import org.apache.commons.lang3.StringUtils;

/**
 * 请求路径补充前缀的代理拦截器工厂
 * 代理请求的uri会添加上设置的前缀
 */
@Slf4j
public class PathPrefixProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    private final static String name = "pathPrefix";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        final String pathPrefix = (String) options;
        Arguments.require(StringUtils.isNotBlank(pathPrefix), "并未配置%s的值".formatted(name));

        return new ProxyInterceptor() {
            @Override
            public void modifyProxyRequest(ProxyRequest proxyRequest) {
                log.debug("{}.modifyProxyRequest 给请求链接添加前缀: {}", name, pathPrefix);
                final String uri = pathPrefix + proxyRequest.getURI();
                proxyRequest.setURI(uri);
                log.debug("请求地址: {}", uri);
            }

        };
    }
}
