package myvertx.gatex.plugin;

import com.google.common.base.Splitter;
import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.Map;

/**
 * 响应301/302时修改Location值的代理拦截器工厂
 *
 * @author zbz
 */
@Slf4j
public class RedirectProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    private final static String name = "redirect";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, final Object options, Injector injector) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));

        @SuppressWarnings("unchecked")
        final Map<String, String> redirectConfig              = (Map<String, String>) options;
        String                    locationConfig              = redirectConfig.get("location");
        String                    locationPrefixConfig        = redirectConfig.get("locationPrefix");
        String                    locationPrefixReplaceConfig = redirectConfig.get("locationPrefixReplace");
        if (StringUtils.isAllBlank(locationConfig, locationPrefixConfig, locationPrefixReplaceConfig)) {
            throw new IllegalArgumentException("请配置location/locationPrefix/locationPrefixReplace其中任意一个的值");
        }

        String locationPrefixReplaceSrcTemp = null;
        String locationPrefixReplaceDstTemp = null;
        if (StringUtils.isNotBlank(locationPrefixReplaceConfig)) {
            Iterator<String> detailIterator = Splitter.on(':').trimResults().omitEmptyStrings().split(locationPrefixReplaceConfig).iterator();
            locationPrefixReplaceSrcTemp = detailIterator.next();
            locationPrefixReplaceDstTemp = detailIterator.hasNext() ? detailIterator.next() : "";
        }
        String locationPrefixReplaceSrc = locationPrefixReplaceSrcTemp;
        String locationPrefixReplaceDst = locationPrefixReplaceDstTemp;

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("redirect.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (statusCode == 301 || statusCode == 302) {
                    String location = proxyResponse.headers().get(HttpHeaders.LOCATION);
                    log.debug("origin location: {}", location);
                    if (StringUtils.isNotBlank(locationConfig)) {
                        location = locationConfig;
                    } else if (StringUtils.isNotBlank(locationPrefixConfig)) {
                        location = locationPrefixConfig + location;
                    } else if (StringUtils.isNotBlank(locationPrefixReplaceConfig)) {
                        location = location.replaceFirst("^" + locationPrefixReplaceSrc, locationPrefixReplaceDst);
                    }
                    log.debug("modified location: {}", location);
                    proxyResponse.headers().set(HttpHeaders.LOCATION, location);
                    return proxyContext.sendResponse();
                }
                return proxyContext.sendResponse();
            }
        };
    }
}
