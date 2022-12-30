package myvertx.gatex.plugin;

import com.google.common.base.Splitter;
import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import org.apache.commons.lang3.StringUtils;
import rebue.wheel.vertx.httpproxy.ProxyInterceptorEx;

import java.util.Iterator;

/**
 * 替换请求路径前缀的代理拦截器工厂
 */
@Slf4j
public class PathPrefixReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "pathPrefixReplace";
    }

    @Override
    public ProxyInterceptorEx create(Vertx vertx, final Object options, Injector injector) {
        if (options == null) {
            log.warn("并未配置替换路径前缀");
            return null;
        }
        final String pathPrefixReplace = (String) options;
        if (StringUtils.isBlank(pathPrefixReplace)) {
            log.warn("并未配置替换路径前缀");
            return null;
        }
        Iterator<String> detailIterator = Splitter.on(':').trimResults().split(pathPrefixReplace).iterator();
        String           src            = detailIterator.next();
        String           dst            = detailIterator.hasNext() ? detailIterator.next() : "";

        return new ProxyInterceptorEx() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("pathPrefixReplace.handleProxyRequest 替换请求链接的前缀: {}", pathPrefixReplace);

                final ProxyRequest request = proxyContext.request();
                final String       uri     = request.getURI().replaceFirst("^" + src, dst);
                request.setURI(uri);
                log.debug("请求地址: {}", uri);

                // 继续拦截器
                return proxyContext.sendRequest();
            }
        };
    }
}
