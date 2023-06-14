package myvertx.gatex.util;

import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.httpproxy.*;
import io.vertx.httpproxy.impl.BufferingWriteStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.drools.fact.RequestFact;
import myvertx.gatex.handler.ParsedBodyHandler;
import myvertx.gatex.handler.RerouteHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.kie.api.runtime.KieSession;
import rebue.wheel.core.DroolsUtils;

import java.util.Map;

@Slf4j
public class ProxyInterceptorUtils {

    /**
     * 处理代理请求
     *
     * @param proxyContext      代理上下文
     * @param parsedBodyHandler 解析body后的处理器
     * @return 代理响应的Future
     */
    public static Future<ProxyResponse> handleRequest(final ProxyContext proxyContext, ParsedBodyHandler parsedBodyHandler) {
        log.debug("handleProxyRequest");

        final ProxyRequest         request = proxyContext.request();
        final Body                 body    = request.getBody();
        final BufferingWriteStream buffer  = new BufferingWriteStream();
        log.debug("准备读取请求的body");
        return body.stream().pipeTo(buffer).compose(v -> {
            final String sBody = buffer.content().toString();
            log.debug("body: {}", sBody);
            if (parsedBodyHandler != null) {
                parsedBodyHandler.handle(proxyContext, sBody);
            }

            // 重新设置body
            request.setBody(Body.body(Buffer.buffer(sBody)));

            // 继续拦截器
            return proxyContext.sendRequest();
        }).recover(err -> {
            final String msg = "解析请求的body失败";
            log.error(msg, err);
            return proxyContext.sendRequest();
        });
    }

    public static ProxyInterceptor createProxyInterceptorA(final String interceptorName, final Object options, ParsedBodyHandler parsedBodyHandler) {
        log.info("createProxyInterceptorA {}: {}", interceptorName, options);
        return new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext proxyContext) {
                log.debug("{}.handleProxyRequest", interceptorName);
                return handleRequest(proxyContext, parsedBodyHandler);
            }
        };
    }

    /**
     * 建立B类型代理拦截器
     * 先请求第一个接口，判断返回值，如果是配置的值，设置 302 状态，响应前转向请求到第二个接口
     *
     * @param interceptorName 拦截器名称
     * @param options         配置选项
     * @param rerouteHandler  重新路由处理器
     * @return 拦截器
     */
    public static ProxyInterceptor createProxyInterceptorB(final String interceptorName, final Object options, Injector injector, RerouteHandler rerouteHandler) {
        log.info("createProxyInterceptorB {}: {}", interceptorName, options);

        Arguments.require(options != null, "并未配置" + interceptorName + "的值");
        Arguments.require(options instanceof Map, interceptorName + "的值必须为Map类型");

        @SuppressWarnings("unchecked") final Map<String, Object> optionsMap    = (Map<String, Object>) options;
        final Object                                             rerouteObject = optionsMap.get("reroute");
        Arguments.require(rerouteObject != null, "并未配置" + interceptorName + ".reroute的值");

        final WebClient webClient = injector.getInstance(WebClient.class);
        return new ProxyInterceptor() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(final ProxyContext proxyContext) {
                log.debug("{}.handleProxyRequest", interceptorName);
                return handleRequest(proxyContext, (proxyContext1, sBody) -> {
                    // 缓存请求的原始body，以便reroute时读取
                    proxyContext.set("originRequestBody", sBody);
                });
            }

            @Override
            public Future<Void> handleProxyResponse(final ProxyContext proxyContext) {
                log.debug("{}.handleProxyResponse", interceptorName);
                ProxyRequest        proxyRequest  = proxyContext.request();
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                log.debug("state code: {}", statusCode);
                if (statusCode != 200) {
                    return proxyContext.sendResponse();
                }
                final Body                 body   = proxyResponse.getBody();
                final BufferingWriteStream buffer = new BufferingWriteStream();
                log.debug("准备读取响应的body");
                return body.stream().pipeTo(buffer)
                        .compose(v -> {
                            // 读取缓存中请求的原始body
                            String sRequestBody = proxyContext.get("originRequestBody", String.class);
                            log.debug("request body: {}", sRequestBody);
                            final String sResponseBody = buffer.content().toString();
                            log.debug("response body: {}", sResponseBody);
                            // 判断第一个接口是否不能处理
                            if (rerouteHandler.isReroute(sRequestBody, sResponseBody)) {
                                log.debug("调用第一个接口结果不能返回，需要转向调用第二个接口");
                                return rerouteHandler.getRerouteRequestBody(sRequestBody)
                                        .compose(sRerouteRequestBody -> {
                                            log.debug("ctx.reroute: {}, {}", rerouteHandler.getReroutePath(), sRerouteRequestBody);
                                            return webClient.request(proxyRequest.getMethod(), 0, "host", rerouteHandler.getReroutePath())
                                                    .sendBuffer(Buffer.buffer(sRerouteRequestBody))
                                                    .compose(bufferHttpResponse -> {
                                                        // 重新设置body
                                                        proxyResponse
                                                                .setStatusCode(200)
                                                                .putHeader("Content-Type", "application/json")
                                                                .setBody(Body.body(bufferHttpResponse.body()));
                                                        return proxyContext.sendResponse();
                                                    }).recover(err -> {
                                                        final String msg = "转向调用第二个接口失败";
                                                        log.error(msg, err);
                                                        proxyResponse.setStatusCode(500);
                                                        return proxyContext.sendResponse();
                                                    });
                                        }).recover(err -> {
                                            final String msg = "获取重新路由的请求body失败";
                                            log.error(msg, err);
                                            proxyResponse.setStatusCode(500);
                                            return proxyContext.sendResponse();
                                        });
                            }

                            // 重新设置body
                            proxyResponse.setBody(Body.body(Buffer.buffer(sResponseBody)));
                            // 继续拦截器
                            return proxyContext.sendResponse();
                        }).recover(err -> {
                            final String msg = "解析响应的body失败";
                            log.error(msg, err);
                            proxyResponse.setStatusCode(500);
                            return Future.succeededFuture();
                        });
            }
        };
    }

    /**
     * 建立C类型代理拦截器
     * 在请求的同时发送一条消息到pulsar服务器
     *
     * @param interceptorName 拦截器名称
     * @param options         配置选项
     * @return 拦截器
     */
    @SneakyThrows
    public static ProxyInterceptor createProxyInterceptorC(final String interceptorName, final Object options, Injector injector) {
        log.info("createProxyInterceptorC {}: {}", interceptorName, options);
        Arguments.require(options != null, "并未配置" + interceptorName + "的值");
        Arguments.require(options instanceof Map, interceptorName + "的值必须为Map类型");

        @SuppressWarnings("unchecked") final Map<String, Object> optionsMap  = (Map<String, Object>) options;
        final Object                                             topicObject = optionsMap.get("topic");
        Arguments.require(topicObject != null, "并未配置" + interceptorName + ".topic的值");
        String sTopic = (String) topicObject;
        Arguments.require(StringUtils.isNotBlank(sTopic), interceptorName + ".topic的值不能为空");

        final PulsarClient pulsarClient = injector.getInstance(PulsarClient.class);
        Producer<String>   producer     = pulsarClient.newProducer(Schema.STRING).topic(sTopic).create();
        return ProxyInterceptorUtils.createProxyInterceptorA(interceptorName, options, (proxyContext, sRequestBody) -> {
            try {
                ProxyRequest proxyRequest = proxyContext.request();
                String       method       = proxyRequest.getMethod().name();
                String       uri          = proxyRequest.getURI();
                log.debug("{}接收到请求: {} {}", interceptorName, uri, sRequestBody);

                KieSession kieSession = DroolsUtils.newKieSession("gatex");
                kieSession.getAgenda().getAgendaGroup(interceptorName + ".ProxyInterceptorC").setFocus();
                RequestFact fact = RequestFact.builder()
                        .method(method)
                        .uri(uri)
                        .body(new JsonObject(sRequestBody))
                        .build();
                kieSession.insert(fact);
                int firedRulesCount = kieSession.fireAllRules();
                log.debug("触发执行了改变body的规则数为{}", firedRulesCount);
                kieSession.dispose();

                log.debug("{}准备发送消息到{}: {}", interceptorName, sTopic, fact.getBody());
                producer.send(fact.getMethod() + ":" + fact.getUri() + " " + fact.getBody());
            } catch (final PulsarClientException e) {
                log.error(interceptorName + "发送消息出现异常", e);
                throw new RuntimeException(e);
            }
        });

    }

}
