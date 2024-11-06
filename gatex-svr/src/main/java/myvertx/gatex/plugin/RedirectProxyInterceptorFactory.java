package myvertx.gatex.plugin;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Splitter;
import com.google.inject.Injector;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;

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

    private static final int[] STATUS_CODES = { 201, 301, 302, 303, 307, 308 };

    @Override
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));

        log.info("{}:{}", name, options);

        String               locationConfig           = null;
        String               locationPrefixConfig     = null;
        String               locationReplaceConfig    = null;

        @SuppressWarnings("unchecked")
        final Map<String, ?> redirectConfig           = (Map<String, ?>) options;
        Object               locationConfigObj        = redirectConfig.get("location");
        Object               locationPrefixConfigObj  = redirectConfig.get("locationPrefix");
        Object               locationReplaceConfigObj = redirectConfig.get("locationReplace");

        if (locationConfigObj != null) {
            locationConfig = locationConfigObj.toString();
        } else if (locationPrefixConfigObj != null) {
            locationPrefixConfig = locationPrefixConfigObj.toString();
        } else if (locationReplaceConfigObj != null) {
            locationReplaceConfig = locationReplaceConfigObj.toString();
        }

        if (StringUtils.isAllBlank(locationConfig, locationPrefixConfig, locationReplaceConfig)) {
            throw new IllegalArgumentException("请配置location/locationPrefix/locationReplace其中任意一个的值");
        }

        String finalLocationConfig        = locationConfig;
        String finalLocationPrefixConfig  = locationPrefixConfig;
        String finalLocationReplaceConfig = locationReplaceConfig;

        String locationReplaceSrcTemp     = "";
        String locationReplaceDstTemp     = "";
        if (StringUtils.isNotBlank(locationReplaceConfig)) {
            Iterator<String> detailIterator = Splitter.on(':').trimResults().omitEmptyStrings()
                    .split(locationReplaceConfig).iterator();
            locationReplaceSrcTemp = detailIterator.next();
            locationReplaceDstTemp = detailIterator.hasNext() ? detailIterator.next() : "";
        }
        String finalLocationReplaceSrc = locationReplaceSrcTemp;
        String finalLocationReplaceDst = locationReplaceDstTemp;
        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                ProxyRequest request    = proxyContext.request();
                String       methodName = request.getMethod().name();
                String       uri        = request.getURI();
                String       methodUri  = methodName + ":" + uri;
                log.debug("{}.handleProxyResponse: {}", name, methodUri);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);

                if (Arrays.binarySearch(STATUS_CODES, statusCode) != -1) {
                    String location = proxyResponse.headers().get(HttpHeaders.LOCATION);
                    log.debug("origin location: {}", location);
                    if (StringUtils.isNotBlank(finalLocationConfig)) {
                        location = finalLocationConfig;
                    } else if (StringUtils.isNotBlank(finalLocationPrefixConfig)) {
                        location = finalLocationPrefixConfig + location;
                    } else if (StringUtils.isNotBlank(finalLocationReplaceConfig)) {
                        location = location.replaceFirst("^" + finalLocationReplaceSrc, finalLocationReplaceDst);
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
