package myvertx.gatex.plugin;

import com.google.common.base.Splitter;
import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;

/**
 * 替换请求路径前缀的代理拦截器工厂
 */
@Slf4j
public class PathPrefixReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    private final static String name = "pathPrefixReplace";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, final Object options, Injector injector) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        final String pathPrefixReplace = (String) options;
        Arguments.require(StringUtils.isNotBlank(pathPrefixReplace), "并未配置%s的值".formatted(name));

        Iterator<String> detailIterator = Splitter.on(':').trimResults().omitEmptyStrings().split(pathPrefixReplace).iterator();
        String           src            = detailIterator.next();
        String           dst            = detailIterator.hasNext() ? detailIterator.next() : "";

        return new ProxyInterceptor() {
            @Override
            public void modifyProxyRequest(ProxyRequest proxyRequest) {
                log.debug("pathPrefixReplace.modifyProxyRequest 替换请求链接的前缀: {}", pathPrefixReplace);
                final String uri = proxyRequest.getURI().replaceFirst("^" + src, dst);
                proxyRequest.setURI(uri);
                log.debug("请求地址: {}", uri);
            }
        };
    }
}
