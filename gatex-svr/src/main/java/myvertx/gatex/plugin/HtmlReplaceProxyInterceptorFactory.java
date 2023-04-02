package myvertx.gatex.plugin;

import com.google.common.base.Splitter;
import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import org.apache.commons.lang3.StringUtils;
import rebue.wheel.vertx.httpproxy.ProxyInterceptorEx;
import rebue.wheel.vertx.httpproxy.impl.BufferingWriteStream;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给html内容中的链接补上前缀的代理拦截器工厂
 * 可以有多个值，第一个值为301/302转向替换的方式
 *
 * @author zbz
 */
@Slf4j
public class HtmlReplaceProxyInterceptorFactory implements GatexProxyInterceptorFactory {
    @Override
    public String name() {
        return "htmlReplace";
    }

    @SuppressWarnings("unchecked")
    @Override
    public ProxyInterceptorEx create(Vertx vertx, final Object options, Injector injector) {
        if (options == null) {
            throw new IllegalArgumentException("并未配置htmlReplace的值");
        }
        if (!(options instanceof final List<?> replaceOptions)) {
            throw new RuntimeException("配置错误: main.routes[].dst.proxyInterceptors[].htmlReplace属性必须是String[]类型");
        }
        Map<String, String> replaces = new LinkedHashMap<>();
        for (final String option : (List<String>) replaceOptions) {
            Iterator<String> detailIterator = Splitter.on(':').trimResults().split(option).iterator();
            String           src            = detailIterator.next();
            String           dst            = detailIterator.hasNext() ? detailIterator.next() : "";
            replaces.put(src, dst);
        }

        return new ProxyInterceptorEx() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("htmlReplace.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (statusCode == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    final Body                 body   = proxyResponse.getBody();
                    final BufferingWriteStream buffer = new BufferingWriteStream();
                    return body.stream().pipeTo(buffer).compose(v -> {
                        String content = buffer.content().toString();
                        for (Map.Entry<String, String> replace : replaces.entrySet()) {
                            content = content.replaceAll(replace.getKey(), replace.getValue());
                        }

                        // 重新设置body
                        proxyResponse.setBody(Body.body(Buffer.buffer(content)));
                        return proxyContext.sendResponse();
                    }).recover(err -> {
                        final String msg = "解析响应的body失败";
                        log.error(msg, err);
                        return proxyContext.sendResponse();
                    });
                } else if (statusCode == 301 || statusCode == 302) {
                    Map.Entry<String, String> replace = null;
                    for (Map.Entry<String, String> entry : replaces.entrySet()) {
                        replace = entry;
                        break;
                    }
                    String location = proxyResponse.headers().get(HttpHeaders.LOCATION).replaceAll(replace.getKey(), replace.getValue());
                    proxyResponse.headers().set(HttpHeaders.LOCATION, location);
                    return proxyContext.sendResponse();
                }
                return proxyContext.sendResponse();
            }
        };
    }
}
