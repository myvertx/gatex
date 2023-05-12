package myvertx.gatex.util;

import com.google.inject.Injector;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyRequest;
import io.vertx.httpproxy.ProxyResponse;
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
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import rebue.wheel.vertx.httpproxy.ProxyInterceptorEx;
import rebue.wheel.vertx.httpproxy.impl.BufferingWriteStream;

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

    public static ProxyInterceptorEx createProxyInterceptorA(final String interceptorName, final Object options, ParsedBodyHandler parsedBodyHandler) {
        log.info("createProxyInterceptorA {}: {}", interceptorName, options);
        return new ProxyInterceptorEx() {
            @Override
            public Future<ProxyResponse> handleProxyRequest(ProxyContext proxyContext) {
                return ProxyInterceptorUtils.handleRequest(proxyContext, parsedBodyHandler);
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
    public static ProxyInterceptorEx createProxyInterceptorB(final String interceptorName, final Object options, RerouteHandler rerouteHandler) {
        log.info("createProxyInterceptorB {}: {}", interceptorName, options);

        Arguments.require(options != null, "并未配置" + interceptorName + "的值");
        Arguments.require(options instanceof Map, interceptorName + "的值必须为Map类型");

        @SuppressWarnings("unchecked") final Map<String, Object> optionsMap    = (Map<String, Object>) options;
        final Object                                             rerouteObject = optionsMap.get("reroute");
        Arguments.require(rerouteObject != null, "并未配置" + interceptorName + ".reroute的值");

        return new ProxyInterceptorEx() {
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
                final ProxyResponse proxyResponse = proxyContext.response();
                final int           statusCode    = proxyResponse.getStatusCode();
                log.debug("state code: {}", statusCode);
                if (statusCode != 200) {
                    return proxyContext.sendResponse();
                }
                final Body                 body   = proxyResponse.getBody();
                final BufferingWriteStream buffer = new BufferingWriteStream();
                log.debug("准备读取响应的body");
                return body.stream().pipeTo(buffer).compose(v -> {
                    String sRequestBody = proxyContext.get("originRequestBody", String.class);
                    log.debug("request body: {}", sRequestBody);
                    final String sResponseBody = buffer.content().toString();
                    log.debug("response body: {}", sResponseBody);
                    // 判断第一个接口是否不能处理
                    if (rerouteHandler.isReroute(sRequestBody, sResponseBody)) {
                        log.debug("调用第一个接口不能处理，准备调用第二个接口");
                        proxyResponse.setStatusCode(302);
                        return Future.succeededFuture();
                    }

                    // 重新设置body
                    proxyResponse.setBody(Body.body(Buffer.buffer(sResponseBody)));

                    // 继续拦截器
                    return proxyContext.sendResponse();
                }).recover(err -> {
                    final String msg = "解析响应的body失败";
                    log.error(msg, err);
                    return proxyContext.sendResponse();
                });
            }

            @SneakyThrows
            @Override
            public boolean beforeResponse(final RoutingContext routingContext, final ProxyContext proxyContext) {
                log.debug("{}.beforeResponse", interceptorName);
                ProxyResponse response   = proxyContext.response();
                final int     statusCode = response.getStatusCode();
                log.debug("state code: {}", statusCode);
                if (statusCode != 302) {
                    routingContext.next();
                    return true;
                }

                // 读取缓存中请求的原始body
                final String originRequestBody = proxyContext.get("originRequestBody", String.class);
                log.debug("originRequestBody: {}", originRequestBody);
                rerouteHandler.getRerouteRequestBody(originRequestBody).onSuccess(sOriginRequestBody -> {
                    routingContext.put("body", sOriginRequestBody);
                    // 如果要改变body，解决content-length不对的问题
                    if (!originRequestBody.equals(sOriginRequestBody)) {
                        routingContext.request().headers().set("Transfer-Encoding", "chunked");
                        routingContext.request().headers().remove("content-length");
                    }
                    log.debug("ctx.reroute: {}, {}", rerouteHandler.getReroutePath(), sOriginRequestBody);
                    routingContext.reroute(rerouteHandler.getReroutePath());
                }).onFailure(routingContext::fail);
                return false;
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
    public static ProxyInterceptorEx createProxyInterceptorC(final String interceptorName, final Object options, Injector injector) {
        log.info("createProxyInterceptorC {}: {}", interceptorName, options);
        Arguments.require(options != null, "并未配置" + interceptorName + "的值");
        Arguments.require(options instanceof Map, interceptorName + "的值必须为Map类型");

        @SuppressWarnings("unchecked") final Map<String, Object> optionsMap  = (Map<String, Object>) options;
        final Object                                             topicObject = optionsMap.get("topic");
        Arguments.require(topicObject != null, "并未配置" + interceptorName + ".topic的值");
        String sTopic = (String) topicObject;
        Arguments.require(StringUtils.isNotBlank(sTopic), interceptorName + ".topic的值不能为空");

        KieServices  kieServices  = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();

        final PulsarClient pulsarClient = injector.getInstance(PulsarClient.class);
        Producer<String>   producer     = pulsarClient.newProducer(Schema.STRING).topic(sTopic).create();
        return ProxyInterceptorUtils.createProxyInterceptorA(interceptorName, options, (proxyContext, sRequestBody) -> {
            try {
                String uri = proxyContext.request().getURI();
                log.debug("{}接收到请求: {} {}", interceptorName, uri, sRequestBody);

                KieSession kieSession = kieContainer.newKieSession(interceptorName);
                RequestFact fact = RequestFact.builder()
                        .uri(uri)
                        .body(new JsonObject(sRequestBody))
                        .build();
                kieSession.insert(fact);
                int firedRulesCount = kieSession.fireAllRules();
                if (firedRulesCount == 1) {
                    log.debug("触发了改变body的规则");
                    sRequestBody = fact.getBody().encode();
                }
                kieSession.dispose();

                log.debug("{}准备发送消息到{}: {}", interceptorName, sTopic, sRequestBody);
                producer.send(uri + " " + sRequestBody);
            } catch (final PulsarClientException e) {
                log.error(interceptorName + "发送消息出现异常", e);
                throw new RuntimeException(e);
            }
        });

    }

}
