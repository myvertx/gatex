package myvertx.gatex.verticle;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.proxy.handler.ProxyHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyInterceptor;
import lombok.extern.slf4j.Slf4j;
import myvertx.gatex.api.GatexFilterFactory;
import myvertx.gatex.api.GatexMatcher;
import myvertx.gatex.api.GatexPredicater;
import myvertx.gatex.api.GatexProxyInterceptorFactory;
import myvertx.gatex.api.GatexRoute;
import myvertx.gatex.api.GatexRoute.Dst;
import myvertx.gatex.config.MainProperties;
import rebue.wheel.vertx.verticle.AbstractWebVerticle;

@Slf4j
public class WebVerticle extends AbstractWebVerticle {
    @Inject
    private MainProperties                            mainProperties;

    private Map<String, GatexPredicater>              _predicaters               = new HashMap<>();

    private Map<String, GatexMatcher>                 _matchers                  = new HashMap<>();

    private Map<String, GatexFilterFactory>           _filterFactories           = new HashMap<>();

    private Map<String, GatexProxyInterceptorFactory> _proxyInterceptorFactories = new HashMap<>();

    /**
     * 根据配置中的路由列表来配置路由
     */
    @Override
    protected void configRouter(final Router router) {
        log.info("注册matcher");
        final ServiceLoader<GatexMatcher> matcherServiceLoader = ServiceLoader.load(GatexMatcher.class);
        matcherServiceLoader.forEach(matcher -> this._matchers.put(matcher.name(), matcher));

        log.info("注册predicater");
        final ServiceLoader<GatexPredicater> predicaterServiceLoader = ServiceLoader.load(GatexPredicater.class);
        predicaterServiceLoader.forEach(predicater -> this._predicaters.put(predicater.name(), predicater));

        log.info("注册过滤器工厂");
        final ServiceLoader<GatexFilterFactory> filterFactoryServiceLoader = ServiceLoader.load(GatexFilterFactory.class);
        filterFactoryServiceLoader.forEach(factory -> this._filterFactories.put(factory.name(), factory));

        log.info("注册代理拦截器工厂");
        final ServiceLoader<GatexProxyInterceptorFactory> proxyInterceptorFactory = ServiceLoader.load(GatexProxyInterceptorFactory.class);
        proxyInterceptorFactory.forEach(factory -> this._proxyInterceptorFactories.put(factory.name(), factory));

        log.info("根据配置中的路由列表来配置路由");
        for (final GatexRoute gatexRouteConfig : this.mainProperties.getRoutes()) {
            // 当前循环配置路由所配置的路由列表
            final List<Route> routes = new LinkedList<>();

            log.info("解析routes[].src");
            if (gatexRouteConfig.getSrc() != null) {
                log.debug("读取routes[].src.path");
                final Object pathObj = gatexRouteConfig.getSrc().getPath();
                if (pathObj != null) {
                    if (pathObj instanceof final String pathStr) {
                        addRoute(router, routes, pathStr, false);
                    } else if (pathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(router, routes, (String) item, false));
                    } else {
                        throw new RuntimeException("配置错误: routes[].src.path属性必须是String或String[]类型");
                    }
                }

                log.debug("读取routes[].src.regexPath");
                final Object regexPathObj = gatexRouteConfig.getSrc().getRegexPath();
                if (regexPathObj != null) {
                    if (regexPathObj instanceof final String pathStr) {
                        addRoute(router, routes, pathStr, true);
                    } else if (regexPathObj instanceof final List<?> pathArr) {
                        pathArr.forEach(item -> addRoute(router, routes, (String) item, true));
                    } else {
                        throw new RuntimeException("配置错误: routes[].src.regexPath属性必须是String或String[]类型");
                    }
                }

                log.debug("读取routes[].src.matchers");
                final Map<String, Object> matchers = gatexRouteConfig.getSrc().getMatchers();
                if (matchers != null) {
                    log.debug("添加匹配器");
                    matchers.entrySet().forEach(entry -> {
                        final GatexMatcher gatexMatcher = this._matchers.get(entry.getKey());
                        routes.forEach(route -> {
                            gatexMatcher.addMatcher(route, entry.getValue());
                        });
                    });
                }
            }

            if (routes.isEmpty()) {
                log.info("此路由为全局路由");
                routes.add(router.route());
            }

            log.info("解析routes[].dst");
            final Dst dst = gatexRouteConfig.getDst();
            Arguments.require(dst != null, "routes[].dst不能为null");
            Arguments.require(dst.getHost() != null, "routes[].dst.host不能为null");

            if ("static".equalsIgnoreCase(dst.getHost())) {
                // 配置静态资源类的路由
                configStaticRoutes(gatexRouteConfig, routes);
            }
            // 代理路由
            else {
                configProxyRoute(gatexRouteConfig, routes, dst);
            }

        }
    }

    /**
     * 配置静态资源类的路由
     *
     * @param gatexRouteConfig 路由配置
     * @param routes           要配置的路由列表
     */
    private void configStaticRoutes(final GatexRoute gatexRouteConfig, final List<Route> routes) {
        log.info("配置静态资源类的路由");
        log.info("遍历当前循环的路由列表中的每一个路由，并添加静态处理器");
        routes.forEach(route -> {
            // 添加断言处理器
            addPredicateHandler(route, gatexRouteConfig.getPredicates());

            // 设置静态路由
            final String routePath = route.getPath();
            log.info("路由的path: {}", routePath);
            route.handler(StaticHandler.create("webroot/" + routePath));
        });
    }

    /**
     * 配置代理类的路由
     *
     * @param gatexRouteConfig 配置
     * @param routes           路由列表
     * @param dst              路由目的地
     */
    private void configProxyRoute(final GatexRoute gatexRouteConfig, final List<Route> routes, final Dst dst) {
        log.info("配置代理类的路由");
        Arguments.require(dst.getPort() != null, "routes[].dst.port不能为null");

        // 获取HttpClientOptions
        final HttpClientOptions httpClientOptions = dst.getClient() == null ? new HttpClientOptions()
                : new HttpClientOptions(JsonObject.mapFrom(dst.getClient()));
        // 创建httpClient
        final HttpClient        httpClient        = this.vertx.createHttpClient(httpClientOptions);
        // 创建Http代理
        final HttpProxy         httpProxy         = HttpProxy.reverseProxy(httpClient)
                .origin(dst.getPort(), dst.getHost());

        // 添加代理拦截器
        addProxyInterceptors(httpProxy, dst.getProxyInterceptors());

        log.info("遍历当前循环的路由列表中的每一个路由，并添加代理处理器");
        routes.forEach(route -> {
            // 添加Body处理器
            // route.handler(BodyHandler.create());
            // 添加断言处理器
            addPredicateHandler(route, gatexRouteConfig.getPredicates());
            // 添加前置过滤器
            addFilters(route, dst.getPreFilters());
            // 设置代理路由
            route.handler(ProxyHandler.create(httpProxy));
            // 添加后置过滤器
            addFilters(route, dst.getPostFilters());
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
    private void addPredicateHandler(final Route route, final Map<String, Object>[] predicates) {
        log.info("给路由添加predicater的处理器");
        if (predicates != null) {
            route.handler(ctx -> {
                log.debug("进入predicate判断");
                // 外循环是and判断(全部条件都为true才为true)，所以没有断言时，默认为true
                boolean andResult = true;
                for (final Map<String, Object> gatexPredicateConfig : predicates) {
                    // 内循环是or判断(只要有一个条件为true就为true)
                    boolean orResult = false;
                    for (final Map.Entry<String, Object> entry : gatexPredicateConfig.entrySet()) {
                        final GatexPredicater gatexPredicate = this._predicaters.get(entry.getKey());
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
        }
    }

    /**
     * 添加代理拦截器
     *
     * @param httpProxy         Http客户端代理
     * @param proxyInterceptors 代理拦截器列表
     */
    private void addProxyInterceptors(final HttpProxy httpProxy, final List<Object> proxyInterceptors) {
        if (proxyInterceptors != null) {
            proxyInterceptors.forEach(item -> {
                String name;
                Object options = null;
                if (item instanceof final String value) {
                    final int index = value.indexOf('=');
                    if (index == -1) {
                        name = value;
                    } else {
                        name    = StringUtils.left(value, index);
                        options = value.substring(index + 1);
                    }
                } else {
                    throw new IllegalArgumentException("暂时不支持配置不为String的代理拦截器");
                }
                log.debug("name,options={},{}", name, options);
                final GatexProxyInterceptorFactory factory = this._proxyInterceptorFactories.get(name);
                if (factory == null) {
                    throw new IllegalArgumentException("找不到名称为" + name + "的代理拦截器");
                }
                final ProxyInterceptor proxyInterceptor = factory.create(options);
                if (proxyInterceptor == null) {
                    return;
                }
                httpProxy.addInterceptor(proxyInterceptor);
            });
        }
    }

    /**
     * 添加过滤器
     *
     * @param route   路由
     * @param filters 要添加的过滤器列表
     */
    private void addFilters(final Route route, final Map<String, Object> filters) {
        if (filters != null) {
            filters.entrySet().forEach(entry -> {
                final GatexFilterFactory factory = this._filterFactories.get(entry.getKey());
                if (factory == null) {
                    throw new IllegalArgumentException("找不到名称为" + entry.getKey() + "的过滤器");
                }
                final Handler<RoutingContext> handler = factory.create(entry.getValue());
                if (handler == null) {
                    return;
                }
                route.handler(handler);
            });
        }
    }

}
