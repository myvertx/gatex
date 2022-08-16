package myvertx.gatex.plugin;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import rebue.wheel.vertx.httpproxy.impl.BufferingWriteStreamEx;

/**
 * 给html内容中的链接补上前缀的代理拦截器工厂
 *
 * @author zbz
 *
 */
@Slf4j
public class HtmlReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "htmlReplace";
    }

    @SuppressWarnings("unchecked")
    @Override
    public ProxyInterceptor create(final Object options) {
        if (options == null) {
            throw new IllegalArgumentException("并未配置htmlReplace的值");
        }

        if (!(options instanceof final List<?> replaceOptions)) {
            throw new RuntimeException("配置错误: main.routes[].dst.proxyInterceptors[].htmlReplace属性必须是String或String[]类型");
        }

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("htmlReplace.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (proxyResponse.getStatusCode() == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    final Body                   body   = proxyResponse.getBody();
                    final BufferingWriteStreamEx buffer = new BufferingWriteStreamEx();
                    return body.stream().pipeTo(buffer).compose(v -> {
                        String content = buffer.content().toString();
                        for (final Object option : replaceOptions) {
                            final Map<String, String> optionMap   = (Map<String, String>) option;
                            final String              regex       = optionMap.get("regex");
                            final String              replacement = optionMap.get("replacement");
                            content = content.replaceAll(regex, replacement);
                        }

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
