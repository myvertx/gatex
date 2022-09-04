package myvertx.gatex.plugin;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;

/**
 * 请求路径补充前缀的代理拦截器工厂
 * 代理请求的uri会添加上设置的前缀
 */
@Slf4j
public class PathPrefixProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "pathPrefix";
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, final Object options) {
        if (options == null) {
            log.warn("并未配置路径前缀");
            return null;
        }
        final String pathPrefix = (String) options;
        if (StringUtils.isBlank(pathPrefix)) {
            log.warn("并未配置路径前缀");
            return null;
        }
        return new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("pathPrefix.handleProxyRequest: {}", proxyContext);

                log.debug("给请求链接添加前缀: {}", pathPrefix);
                proxyContext.request().setURI(pathPrefix + proxyContext.request().getURI());

                // 继续拦截器
                return proxyContext.sendRequest();
            }
        };
    }
}
