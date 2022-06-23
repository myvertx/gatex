package myvertx.gatex.factory;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;

@Slf4j
public class PatchHtmlPathPrefixProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "patchHtmlPathPrefix";
    }

    @Override
    public ProxyInterceptor create(final Object options) {
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
                log.debug("handleProxyRequest: {}", proxyContext);

                final ProxyRequest proxyRequest = proxyContext.request();
                final String       contentType  = proxyRequest.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("content-type: {}", contentType);
                if (StringUtils.isBlank(contentType) || !contentType.contains("text/html")) {
                    log.debug("不是html的请求链接去掉前缀: {}", pathPrefix);
                    proxyContext.request().setURI(proxyContext.request().getURI().replace(pathPrefix, "/"));
                }

                // 继续拦截器
                return proxyContext.sendRequest();
            }

            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("content-type: {}", contentType);
                if (proxyResponse.getStatusCode() == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    final Body                 body   = proxyResponse.getBody();
                    final BufferingWriteStream buffer = new BufferingWriteStream();
                    return body.stream().pipeTo(buffer).compose(v -> {
                        String content = buffer.content().toString();
                        content = content.replaceAll(" href=\"/", " href=\"");
                        content = content.replaceAll(" src=\"/", " src=\"");
                        content = content.replaceAll(" href=\"", " href=\"" + pathPrefix);
                        content = content.replaceAll(" src=\"", " src=\"" + pathPrefix);
                        // 重新设置body
                        proxyResponse.setBody(Body.body(Buffer.buffer(content)));
                        return proxyContext.sendResponse();
                    }).recover(err -> {
                        final String msg = "解析响应的body失败";
                        log.error(msg, err);
                        return proxyContext.sendResponse();
                    });
                }
                return proxyContext.sendResponse();
            }
        };
    }
}
