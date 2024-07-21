package myvertx.gatex.plugin;

import java.util.Map;

import com.google.inject.Injector;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.httpproxy.*;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;

/**
 * 历史模式为HTML5的代理拦截器工厂
 * <a href="https://router.vuejs.org/zh/guide/essentials/history-mode.html#HTML5-%E6%A8%A1%E5%BC%8F">参看Vue文档说明</a>
 *
 * @author zbz
 */
@Slf4j
public class HistoryHtml5ProxyInterceptorFactory implements GatexProxyInterceptorFactory {

    private final static String name = "historyHtml5";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        final WebClient webClient = injector.getInstance(WebClient.class);
        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                ProxyRequest proxyRequest    = proxyContext.request();
                String       proxyMethodName = proxyRequest.getMethod().name();
                String       proxyUri        = proxyRequest.getURI();
                String       proxyMethodUri  = proxyMethodName + ":" + proxyUri;
                log.debug("{}.handleProxyResponse: {}", name, proxyMethodUri);
                ProxyResponse     proxyResponse            = proxyContext.response();
                HttpServerRequest proxiedRequest           = proxyRequest.proxiedRequest();
                String            host                     = dst.getHost();
                int               port                     = dst.getPort();
                String            proxiedRequestMethodName = proxiedRequest.method().name();
                int               statusCode               = proxyResponse.getStatusCode();
                try {
                    if ("GET".equals(proxiedRequestMethodName) && statusCode == 404) {
                        log.info("{}:{}", host, port);
                        return webClient.get(port, host, "/index.html").putHeaders(proxiedRequest.headers())
                                .send().compose(resp -> {
                                    proxyResponse.setStatusCode(200);
                                    for (Map.Entry<String, String> header : resp.headers()) {
                                        proxyResponse.putHeader(header.getKey(), header.getValue());
                                    }
                                    proxyResponse.setBody(Body.body(resp.bodyAsBuffer()));
                                    return proxyContext.sendResponse();
                                }).recover(err -> proxyContext.sendResponse());
                    }
                    return proxyContext.sendResponse();
                } catch (Exception e) {
                    log.error("未知错误", e);
                    proxyResponse.setStatusCode(500);
                }
                return proxyContext.sendResponse();
            }
        };
    }
}
