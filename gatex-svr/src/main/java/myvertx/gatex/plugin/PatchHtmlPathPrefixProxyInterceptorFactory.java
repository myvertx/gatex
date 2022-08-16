package myvertx.gatex.plugin;

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

/**
 * 补充html内容给链接加上前缀的代理拦截器工厂
 * 补充html内容给所有链接加上配置的前缀
 *
 * @author zbz
 *
 */
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
        final String pathPrefixConfig = (String) options;
        if (StringUtils.isBlank(pathPrefixConfig)) {
            log.warn("并未配置路径前缀");
            return null;
        }

        final String pathPrefix = pathPrefixConfig.length() > 1 && pathPrefixConfig.charAt(pathPrefixConfig.length() - 1) == '/'
                ? StringUtils.left(pathPrefixConfig, pathPrefixConfig.length() - 1)
                : pathPrefixConfig;

        return new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("patchHtmlPathPrefix.handleProxyRequest: {}", proxyContext);

                final ProxyRequest proxyRequest = proxyContext.request();
                final String       contentType  = proxyRequest.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("content-type: {}", contentType);
                final String uri = proxyContext.request().getURI();
                log.debug("请求链接{}须去掉前缀: {}", uri, pathPrefix);
                proxyContext.request().setURI(uri.replaceFirst(pathPrefix, ""));

                // 继续拦截器
                return proxyContext.sendRequest();
            }

            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("patchHtmlPathPrefix.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (proxyResponse.getStatusCode() == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    final Body                   body   = proxyResponse.getBody();
                    final BufferingWriteStreamEx buffer = new BufferingWriteStreamEx();
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
