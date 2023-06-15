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
import myvertx.gatex.mo.HtmlReplaceConfigMo;
import myvertx.gatex.mo.RegexReplacementMo;
import myvertx.gatex.util.ConfigUtils;
import org.apache.commons.lang3.StringUtils;

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
        log.info("{}.create: {}", name, options);
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        List<HtmlReplaceConfigMo> htmlReplaceConfigs = new LinkedList<>();
        if (options instanceof List<?> optionsList) {
            Arguments.require(!optionsList.isEmpty(), "并未配置%s的值".formatted(name));
            if (optionsList.get(0) instanceof String) {
                List<RegexReplacementMo> regexReplacements = new LinkedList<>();
                for (String regexReplacement : (List<String>) optionsList) {
                    regexReplacements.add(ConfigUtils.readReplacement(name, regexReplacement));
                }
                htmlReplaceConfigs.add(HtmlReplaceConfigMo.builder()
                    .regexReplacements(regexReplacements)
                    .build());
            } else if (optionsList.get(0) instanceof Map<?, ?>) {
                for (Map<String, Object> replacementMap : (List<Map<String, Object>>) optionsList) {
                    Object                   replacement = replacementMap.get("replacement");
                    List<RegexReplacementMo> regexReplacements;
                    if (replacement instanceof String regexReplacement) {
                        regexReplacements = new LinkedList<>() {{
                            add(ConfigUtils.readReplacement(name, regexReplacement));
                        }};
                    } else if (replacement instanceof List<?> regexReplacementList) {
                        regexReplacements = ConfigUtils.readReplacements(name, (List<String>) regexReplacementList);
                    } else {
                        throw new IllegalArgumentException("配置%s的replacement格式错误".formatted(name));
                    }
                    htmlReplaceConfigs.add(HtmlReplaceConfigMo.builder()
                        .srcPaths(ConfigUtils.readSrcPath(name, replacementMap))
                        .regexReplacements(regexReplacements)
                        .build());
                }
            } else {
                throw new IllegalArgumentException("配置%s的格式错误".formatted(name));
            }
        } else {
            throw new IllegalArgumentException("配置%s的格式错误".formatted(name));
        }
        log.info("{}.config: {}", name, htmlReplaceConfigs);

        return new ProxyInterceptor() {
            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("{}.handleProxyResponse: {}", name, proxyContext);
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                final String        contentType   = proxyResponse.headers().get(HttpHeaders.CONTENT_TYPE);
                log.debug("state code: {}; content-type: {}", statusCode, contentType);
                try {
                    if (statusCode == 200 && StringUtils.isNotBlank(contentType)
                        && (contentType.contains("text/html") || contentType.contains("application/javascript"))) {
                        for (HtmlReplaceConfigMo replaceConfig : htmlReplaceConfigs) {
                            log.debug("判断是否匹配srcPath: {}", replaceConfig.getSrcPaths());
                            if (ConfigUtils.isMatchSrcPath(proxyContext, replaceConfig.getSrcPaths())) {
                                log.debug("匹配srcPath");
                                final Body                 body   = proxyResponse.getBody();
                                final BufferingWriteStream buffer = new BufferingWriteStream();
                                return body.stream().pipeTo(buffer).compose(v -> {
                                    log.debug("解析响应的body成功");
                                    String content = buffer.content().toString();
                                    for (RegexReplacementMo regexReplacementMo : replaceConfig.getRegexReplacements()) {
                                        log.debug("替换文本: {} -> {}", regexReplacementMo.getRegex(), regexReplacementMo.getReplacement());
                                        content = content.replaceAll(regexReplacementMo.getRegex(), regexReplacementMo.getReplacement());
                                    }

                                    // 重新设置body
                                    proxyResponse.setBody(Body.body(Buffer.buffer(content)));
                                    return proxyContext.sendResponse();
                                }).recover(err -> {
                                    final String msg = "解析响应的body失败";
                                    log.error(msg, err);
                                    proxyResponse.setStatusCode(500);
                                    return proxyContext.sendResponse();
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("未知错误", e);
                    proxyResponse.setStatusCode(500);
                }
                return proxyContext.sendResponse();
            }
        };
    }

}
