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
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.mo.SrcPathMo;
import myvertx.gatex.util.ConfigUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    private final static String name = "htmlReplace";

    @Override
    public String name() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        Map<String, String> replacements = new LinkedHashMap<>();
        // 匹配请求URI列表
        List<SrcPathMo> srcPaths = new LinkedList<>();
        if (options instanceof List<?> optionsList) {
            // 读取replacement
            replacements.putAll(ConfigUtils.readReplacement(name, (List<String>) optionsList));
        } else if (options instanceof Map<?, ?> optionsMap) {
            // 读取srcPath
            srcPaths.addAll(ConfigUtils.readSrcPath(name, optionsMap));
            // 读取replacement
            Object replacementListObj = optionsMap.get("replacement");
            if (replacementListObj instanceof List<?> replacementList) {
                replacements.putAll(ConfigUtils.readReplacement(name, (List<String>) replacementList));
            } else {
                throw new IllegalArgumentException("配置%s的replacement格式错误".formatted(name));
            }
        } else {
            throw new IllegalArgumentException("配置%s的格式错误".formatted(name));
        }

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("{}.handleProxyResponse: {}", name, proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                if (statusCode == 200 && StringUtils.isNotBlank(contentType)
                    && (contentType.contains("text/html") || contentType.contains("application/javascript"))) {
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
                            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                                content = content.replaceAll(replacement.getKey(), replacement.getValue());
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
                }
                return proxyContext.sendResponse();
            }
        };
    }

}
