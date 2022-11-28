package myvertx.gatex.plugin;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Injector;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import rebue.wheel.vertx.httpproxy.ProxyInterceptorEx;

/**
 * 替换请求路径的代理拦截器工厂
 * 代理请求的uri会被替换为设置的链接
 */
@Slf4j
public class PathReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "pathReplace";
    }

    @Override
    public ProxyInterceptorEx create(Vertx vertx, final Object options, Injector injector) {
        if (options == null) {
            log.warn("并未配置要替换的路径");
            return null;
        }
        final String replacePath = (String) options;
        if (StringUtils.isBlank(replacePath)) {
            log.warn("并未配置要替换的路径");
            return null;
        }
        return new ProxyInterceptorEx() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("pathReplace.handleProxyRequest 将请求链接替换为: {}", replacePath);

                final ProxyRequest request = proxyContext.request();
                request.setURI(replacePath);

                // 继续拦截器
                return proxyContext.sendRequest();
            }
        };
    }
}
