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
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import rebue.wheel.vertx.httpproxy.impl.BufferingWriteStreamEx;

@Slf4j
public class PatchHtmlBaseProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "patchHtmlBase";
    }

    @Override
    public ProxyInterceptor create(final Object options) {
        if (options == null) {
            log.warn("并未配置路径前缀");
            return null;
        }
        final String baseHrefConfig = (String) options;
        if (StringUtils.isBlank(baseHrefConfig)) {
            log.warn("并未配置路径前缀");
            return null;
        }
        final String baseHref = (baseHrefConfig.length() > 1 && baseHrefConfig.charAt(baseHrefConfig.length() - 1) == '/')
                ? StringUtils.left(baseHrefConfig, baseHrefConfig.length() - 1)
                : baseHrefConfig;

        return new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("patchHtmlBase.handleProxyRequest: {}", proxyContext);

                final ProxyRequest proxyRequest = proxyContext.request();
                final String       contentType  = proxyRequest.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("content-type: {}", contentType);
                final String uri = proxyContext.request().getURI();
                log.debug("请求链接{}须去掉前缀: {}", uri, baseHref);
                proxyContext.request().setURI(uri.replaceFirst(baseHref, ""));

                // 继续拦截器
                return proxyContext.sendRequest();
            }

            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("patchHtmlBase.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (statusCode == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    final Body                   body   = proxyResponse.getBody();
                    final BufferingWriteStreamEx buffer = new BufferingWriteStreamEx();
                    return body.stream().pipeTo(buffer).compose(v -> {
                        String content = buffer.content().toString();
                        content = content.replaceAll("<head>", "<head><base href=\"" + baseHref + '/' + "\">");
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
