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
 * 替换请求路径的代理拦截器工厂
 * 代理请求的uri会被替换为设置的链接
 */
@Slf4j
public class PathReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    private final static String name = "pathReplace";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, final Object options, Injector injector) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        final String replacePath = (String) options;
        Arguments.require(StringUtils.isNotBlank(replacePath), "并未配置%s的值".formatted(name));

        Iterator<String> detailIterator = Splitter.on(':').trimResults().omitEmptyStrings().split(replacePath).iterator();
        String           src            = detailIterator.next();
        String           dst            = detailIterator.hasNext() ? detailIterator.next() : "";
        return new ProxyInterceptor() {
            @Override
            public void modifyProxyRequest(ProxyRequest proxyRequest) {
                log.debug("pathReplace.modifyProxyRequest 替换请求的链接: {}", replacePath);
                String uri = proxyRequest.getURI();
                uri = StringUtils.isBlank(src) ? dst : uri.replaceAll(src, dst);
                proxyRequest.setURI(uri);
                log.debug("请求地址: {}", uri);
            }

        };
    }
}
