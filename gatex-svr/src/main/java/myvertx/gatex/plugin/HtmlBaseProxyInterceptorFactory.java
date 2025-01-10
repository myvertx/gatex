package myvertx.gatex.plugin;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Injector;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.Arguments;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.mo.SrcPathMo;
import myvertx.gatex.util.ConfigUtils;
import rebue.wheel.core.UriUtils;
import rebue.wheel.vertx.util.BodyUtils;

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
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
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
            srcPaths.addAll(ConfigUtils.readSrcPath(optionsMap, name));
        }

        // 填补结束的斜杠
        String baseHref = UriUtils.padEndSlash(baseHrefTemp);

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                ProxyRequest request    = proxyContext.request();
                String       methodName = request.getMethod().name();
                String       uri        = request.getURI();
                String       methodUri  = methodName + ":" + uri;
                log.debug("{}.handleProxyResponse: {}", name, methodUri);
                final ProxyResponse proxyResponse       = proxyContext.response();
                final int           statusCode          = proxyResponse.getStatusCode();
                MultiMap            responseHeaders     = proxyResponse.headers();
                final String        responseContentType = responseHeaders.get(HttpHeaders.CONTENT_TYPE);
                final String        contentEncoding     = responseHeaders.get(HttpHeaders.CONTENT_ENCODING);
                log.debug("state code: {}; content-type: {}", statusCode, responseContentType);
                if (statusCode != 200 || StringUtils.isBlank(responseContentType) || !responseContentType.contains("text/html")) {
                    return proxyContext.sendResponse();
                }
                if (ConfigUtils.isMatchSrcPath(uri, srcPaths)) {
                    final Body                 body                 = proxyResponse.getBody();
                    final BufferingWriteStream bufferingWriteStream = new BufferingWriteStream();
                    return body.stream().pipeTo(bufferingWriteStream).compose(v -> {
                        log.debug("{}解析响应的body成功", name);
                        log.debug("contentEncoding: {}", contentEncoding);

                        // 获取Body内容
                        String content = BodyUtils.getContent(contentEncoding, bufferingWriteStream.content());

                        // 修改内容
                        content = content.replaceAll("<head>", "<head><base href=\"" + baseHref + "\"/>");

                        // 重新设置body
                        proxyResponse.setBody(BodyUtils.newBody(contentEncoding, content));
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
