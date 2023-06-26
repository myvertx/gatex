package myvertx.gatex.plugin;

import com.google.common.base.Splitter;
import com.google.inject.Injector;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;
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
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        final String replacePath = (String) options;
        Arguments.require(StringUtils.isNotBlank(replacePath), "并未配置%s的值".formatted(name));

        Iterator<String> detailIterator = Splitter.on(':').trimResults().omitEmptyStrings().split(replacePath).iterator();
        String           regexTemp      = detailIterator.next();
        String           replacementTemp;
        if (detailIterator.hasNext()) {
            replacementTemp = detailIterator.next();
        } else {
            if (replacePath.contains(":")) {
                replacementTemp = "";
            } else {
                replacementTemp = regexTemp;
                regexTemp = "";
            }
        }
        String regex       = regexTemp;
        String replacement = replacementTemp;
        return new ProxyInterceptor() {
            @Override
            public void modifyProxyRequest(ProxyRequest proxyRequest) {
                log.debug("pathReplace.modifyProxyRequest 替换请求的链接: {}", replacePath);
                String uri = proxyRequest.getURI();
                uri = StringUtils.isBlank(regex) ? replacement : uri.replaceAll(regex, replacement);
                proxyRequest.setURI(uri);
                log.debug("请求地址: {}", uri);
            }

        };
    }
}
