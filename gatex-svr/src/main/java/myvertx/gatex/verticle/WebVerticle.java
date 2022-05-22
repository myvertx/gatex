package myvertx.gatex.verticle;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyContext;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.httpproxy.ProxyResponse;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexPredicater;
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.api.GatexRoute.Dst;
import myvertx.gatex.config.WebProperties;

@Slf4j
public class WebVerticle extends AbstractVerticle {
    public static final String EVENT_BUS_WEB_START = "myvertx.gatex.verticle.web.start";

    private WebProperties      webProperties;
    private HttpServer         httpServer;

    /**
     * 路由器
     */
    private Router             router;
    /**
     * 全局路由
     */
    private Route              globalRoute;

    @Override
    public void start() {
        this.webProperties = config().mapTo(WebProperties.class);
        // 获取HttpServerOptions
        final HttpServerOptions httpServerOptions = this.webProperties.getServer() == null ? new HttpServerOptions()
                : new HttpServerOptions(JsonObject.mapFrom(this.webProperties.getServer()));

        log.info("创建路由器");
        this.router      = Router.router(this.vertx);
        // 全局路由
        this.globalRoute = this.router.route();
        // 记录日志
        if (this.webProperties.getIsLogging()) {
            log.info("开启日志记录");
            this.globalRoute.handler(LoggerHandler.create(this.webProperties.getLoggerFormat()));
        }
        // CORS
        if (this.webProperties.getIsCors()) {
            log.info("开启CORS");
            this.globalRoute.handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET));
        }

        // 配置路由
        configRoutes();

        // 创建HttpServer
        this.httpServer = this.vertx.createHttpServer(httpServerOptions).requestHandler(this.router);
        // 订阅启动监听器的事件
        this.vertx.eventBus()
                .consumer(EVENT_BUS_WEB_START, this::handleStart)
                .completionHandler(this::handleStartCompletion);

        log.info("WebVerticle Started");
    }

    /**
     * 根据配置中的路由列表来配置路由
     */
    private void configRoutes() {
        log.info("注册predicater");
        final ServiceLoader<GatexPredicater> serviceLoader = ServiceLoader.load(GatexPredicater.class);
        final Map<String, GatexPredicater>   predicaters   = new HashMap<>();
        serviceLoader.forEach(predicater -> predicaters.put(predicater.name(), predicater));

        log.info("根据配置中的路由列表来配置路由");
        for (final GatexRoute gatexRouteConfig : this.webProperties.getRoutes()) {
            final List<Route> routes = new LinkedList<>();

            log.debug("读取routes[].src.path");
            if (gatexRouteConfig.getSrc() != null) {
                final Object pathObj = gatexRouteConfig.getSrc().getPath();
                if (pathObj != null) {
                    if (pathObj instanceof final String pathStr) {
                        addRoute(routes, pathStr, false);
                    } else if (pathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(routes, (String) item, false));
                    } else {
                        throw new RuntimeException("配置错误: routes[].src.path属性必须是String或String[]类型");
                    }
                }

                log.debug("读取routes[].src.regexPath");
                final Object regexPathObj = gatexRouteConfig.getSrc().getRegexPath();
                if (regexPathObj != null) {
                    if (regexPathObj instanceof final String pathStr) {
                        addRoute(routes, pathStr, true);
                    } else if (regexPathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(routes, (String) item, true));
                    } else {
                        throw new RuntimeException("配置错误: routes[].src.regexPath属性必须是String或String[]类型");
                    }
                }
            }

            if (routes.isEmpty()) {
                log.info("此路由为全局路由");
                routes.add(this.globalRoute);
            }

            final Dst dst = gatexRouteConfig.getDst();
            Arguments.require(dst != null, "routes[].dst不能为null");
            Arguments.require(dst.getHost() != null, "routes[].dst.host不能为null");
            Arguments.require(dst.getPort() != null, "routes[].dst.port不能为null");

            // 获取HttpClientOptions
            final HttpClientOptions httpClientOptions = dst.getClient() == null ? new HttpClientOptions()
                    : new HttpClientOptions(JsonObject.mapFrom(dst.getClient()));
            // 创建httpClient
            final HttpClient        proxyClient       = this.vertx.createHttpClient(httpClientOptions);
            // 创建Http代理
            final HttpProxy         httpProxy         = HttpProxy.reverseProxy(proxyClient)
                    .origin(dst.getPort(), dst.getHost());
            if (StringUtils.isNotBlank(dst.getPath())) {
                httpProxy.addInterceptor(new ProxyInterceptor() {
                    @Override
                    public Future<ProxyResponse> handleProxyRequest(final ProxyContext context) {
                        log.debug("context.request().getURI()", context.request().getURI());
                        context.request().setURI(dst.getPath().trim() + context.request().getURI());
                        // 继续拦截链
                        return context.sendRequest();
                    }
                });
            }
            routes.forEach(route -> {
                route.handler(ctx -> {
                    log.debug("进入predicate判断");
                    // 外循环是and判断(全部条件都为true才为true)，所以没有断言时，默认为true
                    boolean andResult = true;
                    for (final Map<String, Object> gatexPredicateConfig : gatexRouteConfig.getPredicates()) {
                        // 内循环是or判断(只要有一个条件为true就为true)
                        boolean orResult = false;
                        for (final Map.Entry<String, Object> entry : gatexPredicateConfig.entrySet()) {
                            final GatexPredicater gatexPredicate = predicaters.get(entry.getKey());
                            if (gatexPredicate.test(ctx, entry.getValue())) {
                                orResult = true;
                                break;
                            }
                        }
                        if (!orResult) {
                            andResult = false;
                            break;
                        }
                    }
                    if (andResult) {
                        ctx.next();
                    } else {
                        ctx.end();
                    }
                });
                route.handler(ProxyHandler.create(httpProxy));
            });
        }
    }

    /**
     * 添加路由
     *
     * @param routes  要加入的路由列表
     * @param pathStr 路由监听的路径
     * @param isRegex 路径是否是正则表达式
     */
    private void addRoute(final List<Route> routes, String pathStr, final boolean isRegex) {
        String[] methods = null;
        if (pathStr.startsWith("[")) {
            final String[] pathSplit  = pathStr.split("]");
            final String   methodsStr = pathSplit[0];
            methods = methodsStr.split(",");
            pathStr = pathSplit[1];
        }
        final Route route = isRegex ? this.router.routeWithRegex(pathStr) : this.router.route(pathStr);
        if (methods != null) {
            for (final String method : methods) {
                route.method(HttpMethod.valueOf(method));
            }
        }
        routes.add(route);
    }

    private void handleStart(final Message<Void> message) {
        this.httpServer.listen(res -> {
            if (res.succeeded()) {
                log.info("HTTP server started on port " + res.result().actualPort());
            } else {
                log.error("HTTP server start fail", res.cause());
            }
        });
    }

    private void handleStartCompletion(final AsyncResult<Void> res) {
        if (res.succeeded()) {
            log.info("Event Bus register success: web.start");
        } else {
            log.error("Event Bus register fail: web.start", res.cause());
        }
    }

}
