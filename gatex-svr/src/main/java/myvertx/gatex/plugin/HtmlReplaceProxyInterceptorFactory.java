package myvertx.gatex.plugin;

import java.util.Arrays;
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
import myvertx.gatex.mo.HtmlReplaceConfigMo;
import myvertx.gatex.mo.RegexReplacementMo;
import myvertx.gatex.util.ConfigUtils;
import rebue.wheel.vertx.util.BodyUtils;

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

    private static final int[] STATUS_CODES = { 200, 201, 202 };

    @SuppressWarnings("unchecked")
    @Override
    public ProxyInterceptor create(Vertx vertx, Injector injector, GatexRoute.Dst dst, Object options) {
        log.info("{}.create: {}", name, options);
        Arguments.require(options != null, "并未配置%s的值".formatted(name));
        List<HtmlReplaceConfigMo> htmlReplaceConfigs = new LinkedList<>();
        if (options instanceof List<?> optionsList) {
            Arguments.require(!optionsList.isEmpty(), "并未配置%s的值".formatted(name));
            // 遍历选项列表(每个选项有可能是字符串或是由属性replacement和srcPath构成的Map)
            for (Object item : optionsList) {
                // 如果选项是字符串
                if (item instanceof String regexReplacement) {
                    List<RegexReplacementMo> regexReplacements = List.of(ConfigUtils.parseReplacement(regexReplacement, name));
                    htmlReplaceConfigs.add(HtmlReplaceConfigMo.builder()
                            .regexReplacements(regexReplacements)
                            .build());
                }
                // 如果选项是由属性replacement和srcPath构成的Map
                else if (item instanceof Map<?, ?> replacementMap) {
                    Object                   replacement = replacementMap.get("replacement");
                    List<RegexReplacementMo> regexReplacements;
                    if (replacement instanceof String regexReplacement) {
                        regexReplacements = List.of(ConfigUtils.parseReplacement(regexReplacement, name));
                    } else if (replacement instanceof List<?> regexReplacementList) {
                        regexReplacements = ConfigUtils.parseReplacements((List<String>) regexReplacementList, name);
                    } else {
                        throw new IllegalArgumentException("配置%s的replacement格式错误: %s"
                                .formatted(name, replacement));
                    }
                    htmlReplaceConfigs.add(HtmlReplaceConfigMo.builder()
                            .srcPaths(ConfigUtils.readSrcPath(replacementMap, name))
                            .regexReplacements(regexReplacements)
                            .build());
                } else {
                    throw new IllegalArgumentException("配置%s的格式错误".formatted(name));
                }
            }
        } else {
            throw new IllegalArgumentException("配置%s的格式错误".formatted(name));
        }
        log.info("{}.config: {}", name, htmlReplaceConfigs);

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
                // 不是html或js不进行替换
                if (Arrays.binarySearch(STATUS_CODES, statusCode) != -1 || StringUtils.isBlank(responseContentType)
                        || (!responseContentType.contains("text/html")
                                && !responseContentType.contains("text/javascript")
                                && !responseContentType.contains("application/javascript")
                                && !responseContentType.contains("application/json"))) {
                    return proxyContext.sendResponse();
                }

                final List<RegexReplacementMo> regexReplacements = new LinkedList<>();
                for (HtmlReplaceConfigMo replaceConfig : htmlReplaceConfigs) {
                    log.debug("判断是否匹配srcPath: {}", replaceConfig.getSrcPaths());
                    if (ConfigUtils.isMatchSrcPath(uri, replaceConfig.getSrcPaths())) {
                        log.debug("匹配srcPath，添加替换信息列表");
                        regexReplacements.addAll(replaceConfig.getRegexReplacements());
                    }
                }
                // 不匹配则返回
                if (regexReplacements.isEmpty()) {
                    return proxyContext.sendResponse();
                }

                final Body                 body                 = proxyResponse.getBody();
                final BufferingWriteStream bufferingWriteStream = new BufferingWriteStream();
                return body.stream().pipeTo(bufferingWriteStream).compose(v -> {
                    log.debug("{}解析响应的body成功", name);
                    log.debug("contentEncoding: {}", contentEncoding);

                    // 获取Body内容
                    String content = BodyUtils.getContent(contentEncoding, bufferingWriteStream.content());

                    log.trace("准备修改内容content: {}", content);
                    for (RegexReplacementMo regexReplacementMo : regexReplacements) {
                        log.trace("替换文本: {} -> {}", regexReplacementMo.getRegex(),
                                regexReplacementMo.getReplacement());
                        content = content.replaceAll(regexReplacementMo.getRegex(),
                                regexReplacementMo.getReplacement());
                    }

                    // 重新设置body
                    proxyResponse.setBody(BodyUtils.newBody(contentEncoding, content));
                    return proxyContext.sendResponse();
                }).recover(err -> {
                    final String msg = "解析响应的body失败";
                    log.error(msg, err);
                    proxyResponse.setStatusCode(500);
                    return proxyContext.sendResponse();
                });
            }
        };
    }

}
