package myvertx.gatex.verticle;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.impl.Arguments;
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
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.api.GatexRoute.Dst;
import myvertx.gatex.config.WebProperties;
import myvertx.gatex.predicate.PathPredicate;
import myvertx.gatex.predicate.PredicateFactory;
import myvertx.gatex.predicate.RegexPathPredicate;

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

        log.info("创建路由器");
        this.router      = Router.router(this.vertx);
        // 全局路由
        this.globalRoute = this.router.route();
        // 记录日志
        if (this.webProperties.getIsLogging()) {
            log.info("开启日志记录");
            this.globalRoute.handler(LoggerHandler.create());
        }
        // CORS
        if (this.webProperties.getIsCors()) {
            log.info("开启CORS");
            this.globalRoute.handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET));
        }

        // 注册predicate
        PredicateFactory.create(PathPredicate.class);
        PredicateFactory.create(RegexPathPredicate.class);

        // 配置路由
        configRoutes();

        final List<Route> routes = this.router.getRoutes();
        log.debug("routes: {}", routes);

        // 创建HttpServer
        this.httpServer = this.vertx.createHttpServer().requestHandler(this.router);
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
        log.info("根据配置中的路由列表来配置路由");
        for (final GatexRoute gatexRoute : this.webProperties.getRoutes()) {
            final List<Route> routes = new LinkedList<>();

            log.debug("读取routes[].src.path");
            if (gatexRoute.getSrc() != null) {
                final Object pathObj = gatexRoute.getSrc().getPath();
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
                final Object regexPathObj = gatexRoute.getSrc().getRegexPath();
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
                log.info("匹配所有路由");
                routes.add(this.globalRoute);
            }

            final Dst dst = gatexRoute.getDst();
            Arguments.require(dst != null, "routes[].dst不能为null");
            Arguments.require(dst.getHost() != null, "routes[].dst.host不能为null");
            Arguments.require(dst.getPort() != null, "routes[].dst.port不能为null");

            final HttpClient proxyClient = this.vertx.createHttpClient();
            final HttpProxy  httpProxy   = HttpProxy.reverseProxy(proxyClient)
                    .origin(dst.getPort(), dst.getHost());
            if (StringUtils.isNotBlank(dst.getPath())) {
                httpProxy.addInterceptor(new ProxyInterceptor() {
                    @Override
                    public Future<ProxyResponse> handleProxyRequest(final ProxyContext context) {
                        log.debug("context.request().getURI()", context.request().getURI());
                        context.request().setURI(dst.getPath().trim() + context.request().getURI());
                        return ProxyInterceptor.super.handleProxyRequest(context);
                    }
                });
            }
            routes.forEach(route -> route.handler(ProxyHandler.create(httpProxy)));
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
        this.httpServer.listen(this.webProperties.getPort(), res -> {
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
