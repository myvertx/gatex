package myvertx.gatex.plugin;

import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.mo.SrcPathMo;
import myvertx.gatex.util.ConfigUtils;
import org.apache.commons.lang3.StringUtils;
import rebue.wheel.core.UriUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 给html内容中的head节点补上base节点的代理拦截器工厂
 *
 * @author zbz
 */
@Slf4j
public class HtmlBaseProxyInterceptorFactory implements GatexProxyInterceptorFactory {

    private final static String name = "htmlBase";

    @Override
    public String name() {
        return name;
    }

    @Override
    public ProxyInterceptor create(Vertx vertx, final Object options, Injector injector) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));

        // 匹配请求URI列表
        List<SrcPathMo> srcPaths = new LinkedList<>();
        String          baseHrefTemp;
        if (options instanceof String) {
            baseHrefTemp = (String) options;
            Arguments.require(StringUtils.isNotBlank(baseHrefTemp), "并未配置%s的值".formatted(name));
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> optionsMap = (Map<String, Object>) options;
            baseHrefTemp = (String) optionsMap.get("baseHref");
            Arguments.require(StringUtils.isNotBlank(baseHrefTemp), "并未配置%s的baseHref".formatted(name));
            srcPaths.addAll(ConfigUtils.readSrcPath(name, optionsMap));
        }

        // 填补结束的斜杠
        String baseHref = UriUtils.padEndSlash(baseHrefTemp);

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("htmlBase.handleProxyResponse: {}", proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (statusCode == 200 && StringUtils.isNotBlank(contentType) && contentType.contains("text/html")) {
                    boolean isMatch = false;
                    if (srcPaths.isEmpty()) {
                        isMatch = true;
                    } else {
                        String method = proxyContext.request().getMethod().name();
                        String uri    = proxyContext.request().getURI();
                        for (SrcPathMo srcPath : srcPaths) {
                            if (StringUtils.isNotBlank(srcPath.getMethod()) && !srcPath.getMethod().equalsIgnoreCase(method)) {
                                continue;
                            }
                            if (uri.matches(srcPath.getRegexPath())) {
                                isMatch = true;
                                break;
                            }
                        }
                    }

                    if (isMatch) {
                        final Body                 body   = proxyResponse.getBody();
                        final BufferingWriteStream buffer = new BufferingWriteStream();
                        return body.stream().pipeTo(buffer).compose(v -> {
                            String content = buffer.content().toString();
                            content = content.replaceAll("<head>", "<head><base href=\"" + baseHref + "\">");
                            // 重新设置body
                            proxyResponse.setBody(Body.body(Buffer.buffer(content)));
                            return proxyContext.sendResponse();
                        }).recover(err -> {
                            final String msg = "解析响应的body失败";
                            log.error(msg, err);
                            return proxyContext.sendResponse();
                        });
                    }
                }
                return proxyContext.sendResponse();
            }
        };
    }
}
