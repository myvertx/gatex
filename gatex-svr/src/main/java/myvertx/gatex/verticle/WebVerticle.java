package myvertx.gatex.verticle;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.ext.web.proxy.handler.impl.ProxyHandlerImpl;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyInterceptor;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexPredicater;
import myvertx.gatex.api.GatexPredicaterFactory;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.api.GatexRoute.Dst;
import myvertx.gatex.config.MainProperties;
import org.apache.commons.lang3.StringUtils;
import rebue.wheel.vertx.skywalking.SkyWalkingUtils;
import rebue.wheel.vertx.skywalking.proxyinterceptor.SkyWalkingTraceIdWriteProxyInterceptor;
import rebue.wheel.vertx.verticle.AbstractWebVerticle;

import java.util.*;

@Slf4j
public class WebVerticle extends AbstractWebVerticle {
    @Inject
    private MainProperties mainProperties;

    /**
     * 断言器工厂列表
     */
    private final Map<String, GatexPredicaterFactory>       _predicaterFactories       = new HashMap<>();
    /**
     * 代理拦截器工厂列表
     */
    private final Map<String, GatexProxyInterceptorFactory> _proxyInterceptorFactories = new HashMap<>();

    /**
     * 根据配置中的路由列表来配置路由
     *
     * @param router 路由器
     */
    @Override
    protected void configRouter(final Router router) {
        log.info("注册断言器工厂");
        final ServiceLoader<GatexPredicaterFactory> predicaterServiceLoader = ServiceLoader.load(GatexPredicaterFactory.class);
        predicaterServiceLoader.forEach(factory -> this._predicaterFactories.put(factory.name(), factory));

        log.info("注册代理拦截器工厂");
        final ServiceLoader<GatexProxyInterceptorFactory> proxyInterceptorFactory = ServiceLoader.load(GatexProxyInterceptorFactory.class);
        proxyInterceptorFactory.forEach(factory -> this._proxyInterceptorFactories.put(factory.name(), factory));

        log.info("根据配置中的路由列表来配置路由");
        log.info("********************************************************");
        for (final GatexRoute gatexRouteConfig : this.mainProperties.getRoutes()) {
            // 当前循环配置路由所配置的路由列表
            final List<Route> routes = new LinkedList<>();

            log.info("解析main.routes[].src");
            if (gatexRouteConfig.getSrc() != null) {
                log.debug("读取main.routes[].src.path");
                final Object pathObj = gatexRouteConfig.getSrc().getPath();
                if (pathObj != null) {
                    if (pathObj instanceof final String pathStr) {
                        addRoute(router, routes, pathStr, false);
                    } else if (pathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(router, routes, (String) item, false));
                    } else {
                        throw new RuntimeException("配置错误: main.routes[].src.path属性必须是String或String[]类型");
                    }
                }

                log.debug("读取main.routes[].src.regexPath");
                final Object regexPathObj = gatexRouteConfig.getSrc().getRegexPath();
                if (regexPathObj != null) {
                    if (regexPathObj instanceof final String pathStr) {
                        addRoute(router, routes, pathStr, true);
                    } else if (regexPathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(router, routes, (String) item, true));
                    } else {
                        throw new RuntimeException("配置错误: main.routes[].src.regexPath属性必须是String或String[]类型");
                    }
                }
            }

            if (routes.isEmpty()) {
                log.info("此路由为全局路由");
                routes.add(router.route());
            }

            log.info("解析main.routes[].dst");
            final Dst dst = gatexRouteConfig.getDst();
            Arguments.require(dst != null, "main.routes[].dst不能为null");
            Arguments.require(dst.getHost() != null, "main.routes[].dst.host不能为null");

            try {
                if ("static".equalsIgnoreCase(dst.getHost())) {
                    // 配置静态资源类的路由
                    configStaticRoutes(routes, dst);
                }
                // 代理路由
                else {
                    configProxyRoute(routes, dst);
                }
            } catch (Exception err) {
                log.error("配置路由出错", err);
            }
            log.info("********************************************************");
        }
    }

    /**
     * 配置静态资源类的路由
     *
     * @param routes 要配置的路由列表
     * @param dst    目的地配置
     */
    private void configStaticRoutes(final List<Route> routes, final Dst dst) {
        log.info("配置静态资源类的路由");
        log.info("遍历当前循环的路由列表中的每一个路由，并添加静态处理器");
        routes.forEach(route -> {
            log.info("给路由添加断言处理器");
            addPredicateHandler(route, dst.getPredicates());

            log.info("设置静态根目录");
            final String staticRootDirectory = "webroot" + route.getPath();
            log.info("静态根目录: {}", staticRootDirectory);
            route.handler(StaticHandler.create(staticRootDirectory));
            // 如果静态网站是Html5的HistoryMode，需要添加专门的处理器
            if ("Html5".equalsIgnoreCase(dst.getHistoryMode())) {
                route.handler(ctx -> ctx.response().sendFile(staticRootDirectory + "index.html"));
            }
        });
    }

    /**
     * 配置代理类的路由
     *
     * @param routes 路由列表
     * @param dst    路由目的地
     */
    private void configProxyRoute(final List<Route> routes, final Dst dst) {
        log.info("配置代理类的路由");
        Arguments.require(dst.getPort() != null, "main.routes[].dst.port不能为null");

        log.info("创建HTTP代理");
        // 获取HttpClientOptions
        final HttpClientOptions httpClientOptions = dst.getClient() == null ? new HttpClientOptions()
            : new HttpClientOptions(JsonObject.mapFrom(dst.getClient()));
        // 创建httpClient
        final HttpClient httpClient = this.vertx.createHttpClient(httpClientOptions);

        HttpProxy      httpProxy = HttpProxy.reverseProxy(httpClient);
        RequestOptions requestOptions;
        if (dst.getRequest() == null) {
            requestOptions = new RequestOptions();
        } else {
            requestOptions = new RequestOptions(JsonObject.mapFrom(dst.getRequest()));
        }
        if (requestOptions.getServer() == null)
            requestOptions.setServer(SocketAddress.inetSocketAddress(dst.getPort(), dst.getHost()));

        log.info("给HTTP代理添加代理拦截器");
        if (StringUtils.isNotBlank(dst.getPath())) {
            dst.getFilters().put("pathReplace", dst.getPath());
        }
        if (StringUtils.isNotBlank(dst.getPathPrefix())) {
            dst.getFilters().put("pathPrefix", dst.getPathPrefix());
        }
        if (StringUtils.isNotBlank(dst.getPathPrefixReplace())) {
            dst.getFilters().put("pathPrefixReplace", dst.getPathPrefixReplace());
        }
        addProxyInterceptors(httpProxy, dst);
        log.info("创建代理处理器");
        final ProxyHandler proxyHandler = new ProxyHandlerImpl(httpProxy);

        log.debug("设置httpProxy的请求处理器");
        httpProxy.originRequestProvider((req, client) -> client.request(requestOptions));

        log.info("遍历当前循环的路由列表中的每一个路由，并添加代理处理器");
        routes.forEach(route -> {
            log.debug("路由: {}", route.getPath());
            log.info("给路由添加断言处理器");
            addPredicateHandler(route, dst.getPredicates());
            log.info("给路由添加代理处理器");
//            if (SkyWalkingUtils.isEnabled()) {
//                route.handler(new SkyWalkingTraceIdReadHandler(proxyHandler));
//            } else {
            route.handler(proxyHandler);
//            }
        });
    }

    /**
     * 添加路由
     *
     * @param router  路由器
     * @param routes  要加入的路由列表
     * @param pathStr 路由监听的路径
     * @param isRegex 路径是否是正则表达式
     */
    private void addRoute(final Router router, final List<Route> routes, String pathStr, final boolean isRegex) {
        String[] methods = null;
        if (pathStr.startsWith("[")) {
            final String[] pathSplit  = pathStr.split("]");
            final String   methodsStr = pathSplit[0];
            methods = methodsStr.split(",");
            pathStr = pathSplit[1];
        }
        final Route route = isRegex ? router.routeWithRegex(pathStr) : router.route(pathStr);
        if (methods != null) {
            for (final String method : methods) {
                route.method(HttpMethod.valueOf(method));
            }
        }
        routes.add(route);
    }

    /**
     * 添加断言的处理器
     *
     * @param route      要添加处理器的路由
     * @param predicates 断言列表
     */
    private void addPredicateHandler(final Route route, final Map<String, Object> predicates) {
        if (predicates == null || predicates.isEmpty()) {
            return;
        }

        predicates.forEach((key, value) -> {
            try {
                log.debug("添加断言 {}: {}", key, value);
                final GatexPredicaterFactory factory = this._predicaterFactories.get(key);
                Arguments.require(factory != null, "找不到名为" + key + "的断言工厂");
                log.info("使用{}断言工厂创建断言", key);
                final GatexPredicater predicater = factory.create(this.vertx, value);
                route.handler(ctx -> {
                    log.debug("进入{}断言器判断", factory.name());
                    if (predicater.test(ctx)) {
                        ctx.next();
                    } else {
                        ctx.end();
                    }
                });
            } catch (Exception e) {
                log.error("添加" + key + "断言器异常", e);
                if (mainProperties.getStrict()) throw e;
            }
        });
    }

    /**
     * 添加代理拦截器
     *
     * @param httpProxy Http客户端代理
     * @param dst       目的地的配置
     */
    private void addProxyInterceptors(final HttpProxy httpProxy, Dst dst) {
        final Map<String, Object> proxyInterceptors = dst.getFilters();
        if (proxyInterceptors == null || proxyInterceptors.isEmpty()) {
            return;
        }

//        if (SkyWalkingUtils.isEnabled()) {
//            log.debug("添加将SkyWalking的traceId写入上下文的代理");
//            httpProxy.addInterceptor(new SkyWalkingTraceIdWriteProxyInterceptor());
//        }

        proxyInterceptors.forEach((key, value) -> {
            try {
                log.debug("添加代理拦截器 {}: {}", key, value);
                final GatexProxyInterceptorFactory factory = this._proxyInterceptorFactories.get(key);
                Arguments.require(factory != null, "找不到名为" + key + "的代理拦截器");
                ProxyInterceptor proxyInterceptor = factory.create(this.vertx, this.injector, dst, value);
//                if (SkyWalkingUtils.isEnabled()) {
//                    proxyInterceptor = new SkyWalkingTraceIdReadProxyInterceptor(proxyInterceptor);
//                }
                httpProxy.addInterceptor(proxyInterceptor);
            } catch (Exception e) {
                log.error("添加" + key + "代理拦截器异常", e);
                if (mainProperties.getStrict()) throw e;
            }
        });
    }

}
